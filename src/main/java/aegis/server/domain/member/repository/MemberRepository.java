package aegis.server.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import aegis.server.domain.member.domain.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByOidcId(String oidcId);
}
