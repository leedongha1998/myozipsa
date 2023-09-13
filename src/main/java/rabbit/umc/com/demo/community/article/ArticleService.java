package rabbit.umc.com.demo.community.article;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rabbit.umc.com.config.BaseException;
import rabbit.umc.com.demo.Status;
import rabbit.umc.com.demo.community.*;
import rabbit.umc.com.demo.community.Category.CategoryRepository;
import rabbit.umc.com.demo.community.Comments.CommentRepository;
import rabbit.umc.com.demo.community.domain.*;
import rabbit.umc.com.demo.community.dto.*;
import rabbit.umc.com.demo.mainmission.repository.MainMissionRepository;
import rabbit.umc.com.demo.mainmission.domain.MainMission;
import rabbit.umc.com.demo.mainmission.dto.MainMissionListDto;
import rabbit.umc.com.demo.report.Report;
import rabbit.umc.com.demo.report.ReportRepository;
import rabbit.umc.com.demo.user.Domain.User;
import rabbit.umc.com.demo.user.UserRepository;

import javax.persistence.EntityNotFoundException;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static rabbit.umc.com.config.BaseResponseStatus.*;
import static rabbit.umc.com.demo.Status.*;

@ToString
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final MainMissionRepository mainMissionRepository;
    private final CommentRepository commentRepository;
    private final ImageRepository imageRepository;
    private final LikeArticleRepository likeArticleRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ReportRepository reportRepository;

    public CommunityHomeRes getHome() {
        CommunityHomeRes communityHomeRes = new CommunityHomeRes();
        //상위 4개만 페이징
        PageRequest pageable = PageRequest.of(0,4);

        //STATUS:ACTIVE 인기 게시물 4개만 가져오기
        List<Article> articleList = articleRepository.findPopularArticleLimitedToFour(ACTIVE, pageable);
        //DTO 에 매핑
        communityHomeRes.setPopularArticle(articleList
                        .stream()
                        .map(PopularArticleDto::toPopularArticleDto)
                        .collect(Collectors.toList()));

//        List<PopularArticleDto> popularArticleDtos = articleRepository.findPopularArticleLimitedToFour(ACTIVE,pageable);
//        communityHomeRes.setPopularArticle(popularArticleDtos);

        // STATUS:ACTIVE 미션만 가져오기
        List<MainMission> missionList = mainMissionRepository.findProgressMissionByStatus(ACTIVE);
        //Dto 에 매핑
        communityHomeRes.setMainMission(missionList
                .stream()
                .map(MainMissionListDto::toMainMissionListDto)
                .collect(Collectors.toList()));

        return communityHomeRes;
    }

    public ArticleListsRes getArticles(int page, Long categoryId){
        ArticleListsRes articleLists = new ArticleListsRes();
        Category category = categoryRepository.getReferenceById(categoryId);
        int pageSize = 20; //페이징시 가져올 데이터 수
        PageRequest pageRequest =PageRequest.of(page, pageSize, Sort.by("createdAt").descending());

        // Status:ACTIVE, categoryId에 해당하는 게시물 페이징 해서 가져오기
        List<Article> articlePage = articleRepository.findAllByCategoryIdAndStatusOrderByCreatedAtDesc(categoryId, Status.ACTIVE, pageRequest);
        // DTO 매핑
        List<ArticleListDto> articleListRes = articlePage
                .stream()
                .map(ArticleListDto::toArticleListRes)
                .collect(Collectors.toList());

        //Status:ACTIVE, categoryId에 해당하는 메인미션 가져오기
        MainMission mainMission = mainMissionRepository.findMainMissionsByCategoryIdAndStatus(categoryId, ACTIVE);
        //DTO 에 매핑 (카테고리 이미지, 메인미션 ID, 카테고리 ID, 페이징된 게시물 DTO)
        articleLists.setArticleLists(category.getImage(), mainMission.getId(), category.getUserId(), articleListRes);
        return articleLists;
    }


    public ArticleRes getArticle(Long articleId, Long userId){
        // userId 유저가 articleId 게시물 좋아하는지 체크
        Boolean isLike = likeArticleRepository.existsByArticleIdAndUserId(articleId, userId);

        Article article = articleRepository.findArticleById(articleId);

        // 게시물의 이미지들에 대해 DTO 에 매핑
        List<ArticleImageDto> articleImages = article.getImages()
                .stream()
                .map(ArticleImageDto::toArticleImageDto)
                .collect(Collectors.toList());

        // 게시물의 댓글들에 대해 DTO 매핑
        List<CommentListDto> commentLists = article.getComments()
                .stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt))    //댓글이 오래된 순으로 정렬
                .map(CommentListDto::toCommentListDto)
                .collect(Collectors.toList());

        return ArticleRes.toArticleRes(article, articleImages, commentLists, isLike);

    }


    @Transactional
    public void deleteArticle(Long articleId, Long userId) throws BaseException {
        try {
            Article findArticle = articleRepository.findArticleById(articleId);
            // 게시물 존재 여부 체크
            if (findArticle.getId() == null) {
                throw new NullPointerException("Unable to find Article with id: " + articleId);
            }
            // JWT 가 게시물 작성유저와 동일한지 체크
            if (!findArticle.getUser().getId().equals(userId)) {
                throw new BaseException(INVALID_USER_JWT);
            }
            articleRepository.deleteById(articleId);
        }catch (NullPointerException e){
            throw new BaseException(DONT_EXIST_ARTICLE);
        }
    }

    @Transactional
    public Long postArticle(PostArticleReq postArticleReq, Long userId , Long categoryId ) {
        User user = userRepository.getReferenceById(userId);
        Category category = categoryRepository.getReferenceById(categoryId);
        Article article = new Article();

        // 게시물 생성
        article.setArticle(postArticleReq, user, category);
        articleRepository.save(article);

        // 게시물 이미지 생성
        List<String> imageList = postArticleReq.getImageList();
        for (String imagePath : imageList) {
            Image image = new Image();
            image.setImage(article,imagePath);
            imageRepository.save(image);
        }
        return article.getId();
    }

    @Transactional
    public void updateArticle(Long userId, PatchArticleReq patchArticleReq, Long articleId) throws BaseException {
        try {
            Article findArticle = articleRepository.findArticleById(articleId);
            //글 존재 여부 체크
            if (findArticle.getId() == null) {
                throw new NullPointerException("Unable to find Article with id:" + articleId);
            }
            // JWT 가 글 작성 유저와 동일한지 체크
            if (!findArticle.getUser().getId().equals(userId)) {
                throw new BaseException(INVALID_USER_JWT);
            }

            findArticle.setTitle(patchArticleReq.getArticleTitle());
            findArticle.setContent(patchArticleReq.getArticleContent());

            List<Image> findImages = imageRepository.findAllByArticleId(articleId);

            // 업데이트할 이미지 ID 목록을 생성
            Set<Long> updatedImageIds = patchArticleReq.getImageList()
                    .stream()
                    .map(ChangeImageDto::getImageId)
                    .collect(Collectors.toSet());

            // 기존 이미지 중 업데이트할 이미지 ID 목록에 포함되지 않은 이미지를 삭제
            List<Image> imagesToDelete = findImages
                    .stream()
                    .filter(image -> !updatedImageIds.contains(image.getId()))
                    .collect(Collectors.toList());

            // 이미지 삭제
            imageRepository.deleteAll(imagesToDelete);

            // 업데이트할 이미지를 기존 이미지와 매칭하여 업데이트 또는 추가
            for (ChangeImageDto imageDto : patchArticleReq.getImageList()) {
                Image findImage = findImages
                        .stream()
                        .filter(image -> image.getId().equals(imageDto.getImageId()))
                        .findFirst()
                        .orElse(new Image()); // 새 이미지 생성

                findImage.setArticle(findArticle);
                findImage.setFilePath(imageDto.getFilePath());

                // 이미지 저장 또는 업데이트
                imageRepository.save(findImage);
            }
        }catch (NullPointerException e){
            throw new BaseException(DONT_EXIST_ARTICLE);
        }
    }

    @Transactional
    public void reportArticle(Long userId, Long articleId) throws BaseException {
        try {
            Article article = articleRepository.getReferenceById(articleId);
            // 게시물 존재 체크
            if(article.getId() == null){
                throw new EntityNotFoundException("Unable to find article with id:" + articleId);
            }
            User user = userRepository.getReferenceById(userId);

            // 이미 신고한 게시물인지 체크
            Boolean isReportExists = reportRepository.existsByUserAndArticle(user, article);
            if (isReportExists) {
                throw new BaseException(FAILED_TO_REPORT);

            } else {
                Report report = new Report();
                report.setUser(user);
                report.setArticle(article);
                reportRepository.save(report);

                // 신고 횟수 15회 이상 시 게시물 status 변경 로직  [ACTIVE -> INACTIVE]
                int reportCount = reportRepository.countByArticleId(articleId);
                if (reportCount > 14) {
                    article.setStatus(INACTIVE);
                }
            }
        }catch (EntityNotFoundException e){
            throw new BaseException(DONT_EXIST_ARTICLE);
        }
    }


    @Transactional
    public void likeArticle(Long userId, Long articleId) throws BaseException {
        try {
            User user = userRepository.getReferenceById(userId);
            Article article = articleRepository.getReferenceById(articleId);

            //게시물 존재 체크
            if (article.getId() == null) {
                throw new EntityNotFoundException("Unable to find article with id:" + articleId);
            }
            LikeArticle existlikeArticle = likeArticleRepository.findLikeArticleByArticleIdAndUserId(articleId, userId);
            //이미 좋아한 게시물인지 체크
            if (existlikeArticle != null) {
                throw new BaseException(FAILED_TO_LIKE);
            }

            //게시물 좋아요 저장
            LikeArticle likeArticle = new LikeArticle();
            likeArticle.setLikeArticle(user, article);
            likeArticleRepository.save(likeArticle);

        }catch (EntityNotFoundException e){
            throw new BaseException(DONT_EXIST_ARTICLE);
        }
    }

    @Transactional
    public void unLikeArticle(Long userId, Long articleId) throws BaseException {
        try {
            Article article = articleRepository.getReferenceById(articleId);
            //게시물 존재 체크
            if (article.getId() == null) {
                throw new EntityNotFoundException("Unable to find article with id:" + articleId);
            }
            LikeArticle existlikeArticle = likeArticleRepository.findLikeArticleByArticleIdAndUserId(articleId, userId);
            //좋아요 했던 게시물인지 체크
            if (existlikeArticle == null) {
                throw new BaseException(FAILED_TO_UNLIKE);
            }
            likeArticleRepository.delete(existlikeArticle);
        }catch (EntityNotFoundException e){
            throw new BaseException(DONT_EXIST_ARTICLE);
        }
    }

    public List<GetPopularArticleRes> popularArticle(int page) throws BaseException{
        int pageSize = 20; //페이징시 가져올 데이터 수
        PageRequest pageRequest =PageRequest.of(page, pageSize, Sort.by("createdAt").descending());

        //todo :  현재는 인기 게시물 기준이 좋아요 5개 이상
        //Status:ACTIVE 좋아요 5개 이상인 게시물 최신순으로 정렬해서 가져오기
        List<Article> popularArticles = articleRepository.findArticleLimited20(ACTIVE, pageRequest);

        //DTO 매핑
        List<GetPopularArticleRes> getPopularArticleRes = popularArticles
                .stream()
                .map(GetPopularArticleRes::toPopularArticle)
                .collect(Collectors.toList());

        // 더 이상 페이지가 없을 때 처리
        if (popularArticles.size() ==0 ) {
            throw new BaseException(END_PAGE);
        }

        return getPopularArticleRes;
    }
}