package com.tejesh.timetable.repository;

import com.tejesh.timetable.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Spring auto-generates the SQL implementation at startup — no manual SQL needed.
 * All standard CRUD methods ({@code save}, {@code findById}, {@code delete}, etc.)
 * are inherited from {@link JpaRepository}.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Looks up a user by their unique username for login / session loading.
     *
     * <p>SQL generated: {@code SELECT * FROM users WHERE username = ?}
     *
     * @param username the unique login identifier (case-sensitive)
     * @return an {@link Optional} containing the User if found, or empty
     */
    Optional<User> findByUsername(String username);
}
