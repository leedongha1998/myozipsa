package rabbit.umc.com.demo.mainmission.domain;

import lombok.Getter;
import lombok.Setter;
import rabbit.umc.com.config.BaseTimeEntity;
import rabbit.umc.com.demo.Status;
import rabbit.umc.com.demo.community.domain.Category;
import rabbit.umc.com.demo.mainmission.dto.PostMainMissionReq;

import javax.persistence.*;
import java.time.LocalDate;

import static javax.persistence.GenerationType.IDENTITY;

@Entity
@Getter@Setter
@Table(name = "main_mission")
public class MainMission extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = IDENTITY)
    @Column(name = "main_mission_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false)
    private Boolean lastMission;

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private LocalDate startAt;
    @Column(nullable = false)
    private LocalDate endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status= Status.ACTIVE;

    //Setter
    public void setMainMission(PostMainMissionReq postMainMissionReq, Category category){
        this.category = category;
        this.startAt = postMainMissionReq.getMissionStartTime();
        this.endAt = postMainMissionReq.getMissionEndTime();
        this.title = postMainMissionReq.getMainMissionTitle();
        this.content = postMainMissionReq.getMainMissionContent();
        this.lastMission = postMainMissionReq.getLastMission();
    }

    //비즈니스 로직
    public void inActive(){
        this.status = Status.INACTIVE;
    }


}
