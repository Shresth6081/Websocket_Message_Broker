package com.example.chatservice.repository;

import com.example.chatservice.model.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
    List<RoomMember> findByUsername(String username);
    List<RoomMember> findByRoomId(Long roomId);
    boolean existsByRoomIdAndUsername(Long roomId, String username);
}
