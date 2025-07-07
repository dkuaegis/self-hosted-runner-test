package aegis.server.domain.discord.service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import aegis.server.domain.discord.domain.DiscordVerification;
import aegis.server.domain.discord.dto.response.DiscordIdResponse;
import aegis.server.domain.discord.dto.response.DiscordVerificationCodeResponse;
import aegis.server.domain.discord.repository.DiscordVerificationRepository;
import aegis.server.domain.member.domain.Member;
import aegis.server.domain.member.repository.MemberRepository;
import aegis.server.global.exception.CustomException;
import aegis.server.global.exception.ErrorCode;
import aegis.server.global.security.oidc.UserDetails;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DiscordService {

    private final DiscordVerificationRepository discordVerificationRepository;
    private final MemberRepository memberRepository;

    private final Map<Long, Object> verificationCodeLocks = new ConcurrentHashMap<>();

    public DiscordIdResponse getDiscordId(UserDetails userDetails) {
        Member member = memberRepository
                .findById(userDetails.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        return DiscordIdResponse.of(member.getDiscordId());
    }

    @Transactional
    public DiscordVerificationCodeResponse createVerificationCode(UserDetails userDetails) {
        Member member = memberRepository
                .findById(userDetails.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        synchronized (getLock(member.getId())) {
            Optional<DiscordVerification> optionalVerification =
                    discordVerificationRepository.findByMemberId(member.getId());

            DiscordVerification discordVerification;
            if (optionalVerification.isPresent()) {
                discordVerification = optionalVerification.get();
            } else {
                String code = generateUniqueCode();
                discordVerification = DiscordVerification.of(code, member.getId());
            }

            discordVerificationRepository.save(discordVerification);

            return DiscordVerificationCodeResponse.of(discordVerification.getCode());
        }
    }

    // DiscordSlashCommandListener에서 사용
    @Transactional
    public void verifyAndUpdateDiscordId(String verificationCode, String discordId) {
        DiscordVerification discordVerification = discordVerificationRepository
                .findById(verificationCode)
                .orElseThrow(NoSuchElementException::new); // 메서드가 try-catch문 안에서 호출되므로 여기서 CustomException을 발생시키지 않는다

        Member member = memberRepository
                .findById(discordVerification.getMemberId())
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));

        member.updateDiscordId(discordId);

        discordVerificationRepository.delete(discordVerification);

        log.info("[DiscordService] 디스코드 연동 완료: memberId={}, discordId={}", member.getId(), member.getDiscordId());
    }

    private String generateUniqueCode() {
        String code;
        int maxAttempts = 100;
        int attempts = 0;
        do {
            if (attempts++ >= maxAttempts) {
                throw new CustomException(ErrorCode.DISCORD_CANNOT_ISSUE_VERIFICATION_CODE);
            }
            code = generateRandomCode();
        } while (discordVerificationRepository.existsById(code));
        return code;
    }

    private String generateRandomCode() {
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    private Object getLock(Long memberId) {
        return verificationCodeLocks.computeIfAbsent(memberId, key -> new Object());
    }
}
