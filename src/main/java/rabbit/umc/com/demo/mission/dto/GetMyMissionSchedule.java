package rabbit.umc.com.demo.mission.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import rabbit.umc.com.demo.schedule.domain.Schedule;

import javax.persistence.Access;
import java.time.format.DateTimeFormatter;

@Getter
@Setter
@AllArgsConstructor
public class GetMyMissionSchedule {
    private long id;
    private String title;
    private String when;

    public static GetMyMissionSchedule toGetMyMissionSchedule(Schedule schedule){
        String when = schedule.getStartAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return new GetMyMissionSchedule(
                schedule.getId(),
                schedule.getTitle(),
                when
        );
    }
}
