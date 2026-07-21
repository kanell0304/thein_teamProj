package com.anything.momeogji.mapper.chat;

import com.anything.momeogji.dto.chat.ChatRoomListItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 참가자 수와 최근 메시지를 한 번에 조회하는 읽기 전용 MyBatis Mapper. */
@Mapper
public interface ChatRoomQueryMapper {

    List<ChatRoomListItemResponse> findAllByMemberId(@Param("memberId") Long memberId);
}
