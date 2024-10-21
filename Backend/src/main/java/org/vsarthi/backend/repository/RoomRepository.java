    package org.vsarthi.backend.repository;

    import org.springframework.data.jpa.repository.JpaRepository;
    import org.vsarthi.backend.model.Room;
    import org.vsarthi.backend.model.Users;

    import java.util.List;
    import java.util.Optional;

    public interface RoomRepository extends JpaRepository<Room, Long> {
        List<Room> findByCreatorId(Long creatorId);
        Optional<Room> findByShareableLink(String shareableLink);


        List<Room> findAllByJoinedUsersContaining(Users user);
    }