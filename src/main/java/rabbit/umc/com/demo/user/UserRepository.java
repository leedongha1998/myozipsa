package rabbit.umc.com.demo.user;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import rabbit.umc.com.demo.community.domain.Article;
import rabbit.umc.com.demo.user.Domain.User;
import rabbit.umc.com.demo.user.Dto.UserCommentedArticleListResDto;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User findByKakaoId(Long kakaoId);
    boolean existsByKakaoId(Long kakaoId);
    boolean existsByUserName(String userName);

    //유저가 쓴 글 조회
    @Query("SELECT a FROM Article a " +
            "WHERE a.user.id = :userId " +
            "AND a.status = 'ACTIVE' " +
            "ORDER BY a.createdAt DESC")
    List<Article> findArticlesByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, PageRequest pageRequest);

    //유저가 댓글을 남긴 글 조회
        @Query("SELECT a, max(c.createdAt) as maxCreatedAt " +
            "FROM Article a " +
            "JOIN a.comments c " +
            "WHERE c.user.id = :userId " +
            "AND a.status = 'ACTIVE' " +
            "GROUP BY a " +
            "ORDER BY maxCreatedAt DESC")
    List<UserCommentedArticleListResDto> findCommentedArticlesByUserId(@Param("userId") Long userId, PageRequest pageRequest);

    @Query("SELECT CASE WHEN u.jwtRefreshToken = :token " +
            "THEN true ELSE false END " +
            "FROM User u WHERE u.id = :userId")
    boolean checkJwtRefreshTokenMatch(@Param("userId") Long userId, @Param("token") String token);
}