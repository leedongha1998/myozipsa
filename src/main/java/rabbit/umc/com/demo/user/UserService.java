package rabbit.umc.com.demo.user;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import rabbit.umc.com.config.BaseException;
import rabbit.umc.com.config.BaseResponseStatus;
import rabbit.umc.com.config.secret.Secret;
import rabbit.umc.com.demo.Status;
import rabbit.umc.com.demo.community.domain.Article;
import rabbit.umc.com.demo.user.Domain.User;
import rabbit.umc.com.demo.user.Dto.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static rabbit.umc.com.config.BaseResponseStatus.*;

@Service
@Slf4j
public class UserService {
    @Autowired
    UserRepository userRepository;

    //유저 아이디로 User 객체 찾기
    public User findUser(Long id) throws BaseException {
        Optional<User> optionalUser = userRepository.findById(id);
        User user;
        if (optionalUser.isPresent()) {
            user = optionalUser.get();
        } else {
            log.info("데이터 베이스에서 찾을 수 없는 user id입니다.");
            throw new BaseException(BaseResponseStatus.USERS_EMPTY_USER_ID);
        }

        if(user.getStatus() == Status.INACTIVE){
            log.info("탈퇴한 회원입니다.");
            throw new BaseException(BaseResponseStatus.INVALID_USER_ID);
        }
        return user;
    }

    //유저 email, nickname 저장

    public void getEmailandNickname(Long userId, UserEmailNicknameReqDto userEmailNicknameReqDto) throws BaseException {
        User user = findUser(userId);
        isExistSameNickname(userEmailNicknameReqDto.getUserName());

        user.setUserName(userEmailNicknameReqDto.getUserName());
        user.setUserEmail(userEmailNicknameReqDto.getUserEmail());

        userRepository.save(user);
    }

    public void isExistSameNickname(String nickname) throws BaseException {
        boolean isExistSameName = userRepository.existsByUserName(nickname);
        if(isExistSameName == true){
            log.info("중복된 닉네임입니다.");
            System.out.println("중복된 닉네임: "+nickname);
            throw new BaseException(POST_USERS_EXISTS_NICKNAME);
        }
    }

    //이메일 형식 검증
    public void isEmailVerified(UserEmailNicknameReqDto userEmailNicknameReqDto) throws BaseException {
        String email = userEmailNicknameReqDto.getUserEmail();

        // 이메일 형식을 검증하기 위한 정규 표현식
        String emailPattern = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";

        // 정규 표현식과 입력된 이메일을 비교하여 형식을 검증
        Pattern pattern = Pattern.compile(emailPattern);
        Matcher matcher = pattern.matcher(email);

        if (!matcher.matches()) {
            log.info("이메일 형식이 잘못되었습니다.");
            throw new BaseException(POST_USERS_INVALID_EMAIL);
        }
    }

    //프로필 이미지 수정
    @Transactional
    public void updateProfileImage(Long userId, String newProfileImage) throws BaseException {
        try{
            User user = findUser(userId);
            user.setUserProfileImage(newProfileImage);
            userRepository.save(user);
        }
        catch (RuntimeException e) {
            String rollbackReason = e.getMessage();
            log.info(rollbackReason);
        }
    }

    //닉네임 수정
    @Transactional
    public void updateNickname(Long userId, String newNickname) throws BaseException {
        User user = findUser(userId);
        isExistSameNickname(newNickname);

        user.setUserName(newNickname);
        userRepository.save(user);
    }

    //유저 프로필 조회
    public UserGetProfileResDto getProfile(Long id) throws BaseException {
        User user = findUser(id);
        UserGetProfileResDto userGetProfileResDto = new UserGetProfileResDto(user.getUserEmail(), user.getUserName(), user.getUserProfileImage());
        return userGetProfileResDto;
    }

    public List<UserArticleListResDto> getArticles(int page, Long userId) {

        int pageSize = 20;

        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());

        List<Article> articlePage = userRepository.findArticlesByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        List<UserArticleListResDto> userArticleListResDtos = articlePage.stream()
                .map(UserArticleListResDto::toArticleListRes)
                .collect(Collectors.toList());

        return userArticleListResDtos;
    }

    public List<UserArticleListResDto> getCommentedArticles(int page, Long userId) {
        int pageSize = 20;

        PageRequest pageRequest = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());

        List<UserCommentedArticleListResDto> articlePage = userRepository.findCommentedArticlesByUserId(userId, pageRequest);

        List<UserArticleListResDto> userArticleListResDtos = articlePage.stream()
                .map(UserCommentedArticleListResDto::toArticleListRes)
                .collect(Collectors.toList());

        return userArticleListResDtos;
    }

    //access token 재발급
    public boolean isReissueAllowed (Long userId, String refreshToken) throws BaseException {
        //인자로 받은 refresh token과 해당 user id의db에 있는 refresh token이 일치한지 검사
        boolean tokenMatch = userRepository.checkJwtRefreshTokenMatch(userId, refreshToken);
        if(tokenMatch){
            try{
                Jwts.parser()
                        .setSigningKey(Secret.JWT_SECRET_KEY) // 서명 키 지정
                        .parseClaimsJws(refreshToken);
            } catch (Exception ex) {
                log.info("refresh token이 유효하지 않습니다.");
                return false;
            }
        } else{
            log.info("데이터베이스의 리프레시 토큰과 일치하지 않습니다.");
            throw new BaseException(INVALID_JWT_REFRESH);
        }
        return true;
    }

    //refresh token db에 저장
    public void saveRefreshToken(Long userId, String token) throws BaseException {
        User user = findUser(userId);
        user.setJwtRefreshToken(token);
        userRepository.save(user);
    }

    public void delRefreshToken(User user){
        user.setJwtRefreshToken(null);
        userRepository.save(user);
    }

}
