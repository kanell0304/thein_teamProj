package com.anything.momeogji.service.recommendation;

import com.anything.momeogji.mydata.processing.model.MyDataRestaurantData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 개인 옵션 제출 시 생성된 MyData 음식점 목록을 모임 종료 전까지 메모리에 임시 보관한다.
 *
 * <p>사용자 ID는 저장소 내부 키로만 사용한다. 추천 요청을 조립할 때는 사용자별 목록을
 * 입력 사용자 순서대로 연결하며 사용자 식별자는 반환하지 않는다.</p>
 */
@Component
public class MeetupMyDataResultStore {

    private final Map<Long, Map<Long, List<MyDataRestaurantData>>> resultsByMeetup =
            new ConcurrentHashMap<>();

    /**
     * 한 사용자의 MyData 음식점 목록을 모임별로 저장한다.
     *
     * <p>같은 모임과 사용자 키에 결과가 이미 있으면 새 성공 결과로 교체한다.
     * 빈 목록도 MyData 처리가 정상 완료된 결과로 저장한다.</p>
     *
     * @param meetupId 결과가 사용될 모임 ID
     * @param userId MyData를 처리한 사용자 ID
     * @param restaurants 음식점명과 음식 카테고리만 포함한 결과 목록
     */
    public void save(
            Long meetupId,
            Long userId,
            List<MyDataRestaurantData> restaurants
    ) {
        requirePositiveId(meetupId, "meetupId");
        requirePositiveId(userId, "userId");
        if (restaurants == null) {
            throw new IllegalArgumentException("restaurants는 null일 수 없습니다.");
        }

        // 사용자 결과를 변경할 수 없게 복사한 뒤 모임 안의 사용자 키로 저장한다.
        resultsByMeetup
                .computeIfAbsent(meetupId, ignored -> new ConcurrentHashMap<>())
                .put(userId, List.copyOf(restaurants));
    }

    /**
     * 지정한 사용자들의 MyData 음식점 목록을 하나의 목록으로 연결한다.
     *
     * <p>사용자 ID 목록 순서와 각 사용자 내부 음식점 순서를 유지한다.
     * 저장 결과가 없는 사용자는 건너뛰고 음식점 중복은 제거하지 않는다.</p>
     *
     * @param meetupId 결과를 조회할 모임 ID
     * @param userIds 추천에 참여하는 사용자 ID 목록
     * @return 사용자 구분을 제거해 하나로 연결한 불변 음식점 목록
     */
    public List<MyDataRestaurantData> findAll(
            Long meetupId,
            List<Long> userIds
    ) {
        requirePositiveId(meetupId, "meetupId");
        if (userIds == null) {
            throw new IllegalArgumentException("userIds는 null일 수 없습니다.");
        }

        Map<Long, List<MyDataRestaurantData>> resultsByUser =
                resultsByMeetup.get(meetupId);
        if (resultsByUser == null) {
            return List.of();
        }

        List<MyDataRestaurantData> combinedResults = new ArrayList<>();
        for (Long userId : userIds) {
            requirePositiveId(userId, "userId");

            // 결과가 있는 사용자만 순서대로 연결하고 같은 음식점도 그대로 보존한다.
            List<MyDataRestaurantData> userResults = resultsByUser.get(userId);
            if (userResults != null) {
                combinedResults.addAll(userResults);
            }
        }

        return List.copyOf(combinedResults);
    }

    /**
     * 최종 확정된 모임의 모든 MyData 임시 결과를 제거한다.
     *
     * @param meetupId 제거할 모임 ID
     */
    public void clear(Long meetupId) {
        requirePositiveId(meetupId, "meetupId");
        resultsByMeetup.remove(meetupId);
    }

    /**
     * 저장소 키로 사용하는 ID가 양수인지 확인한다.
     *
     * @param id 검사할 ID
     * @param fieldName 오류 메시지에 표시할 필드명
     */
    private void requirePositiveId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + "는 1 이상이어야 합니다.");
        }
    }
}
