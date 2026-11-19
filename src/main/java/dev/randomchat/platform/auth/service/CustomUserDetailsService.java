package dev.randomchat.platform.auth.service;

import dev.randomchat.platform.user.entity.AccountStatus;
import dev.randomchat.platform.user.entity.UserEntity;
import dev.randomchat.platform.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads user details from the database for Spring Security authentication.
 * The "username" in Spring Security terms is the user's email address.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UserEntity user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        boolean isActive = user.getStatus() == AccountStatus.ACTIVE;

        return new User(
                user.getId().toString(),  // Use user ID as the principal name
                user.getPasswordHash(),
                isActive,                 // enabled
                true,                     // accountNonExpired
                true,                     // credentialsNonExpired
                isActive,                 // accountNonLocked
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
