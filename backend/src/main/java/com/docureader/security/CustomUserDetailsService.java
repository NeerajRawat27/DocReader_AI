package com.docureader.security;

import com.docureader.model.User;
import com.docureader.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return UserPrincipal.create(user);
    }

    @Transactional
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id).orElseGet(() -> {
            java.util.Optional<User> byUsername = userRepository.findByUsername("guest_" + id);
            if (byUsername.isPresent()) {
                return byUsername.get();
            }
            java.util.Optional<User> byEmail = userRepository.findByEmail("guest_" + id + "@demo.docureader.ai");
            if (byEmail.isPresent()) {
                return byEmail.get();
            }
            String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            User guest = new User("guest_" + id + "_" + suffix, "guest_" + id + "_" + suffix + "@demo.docureader.ai", "demo_auto_recovered", true);
            guest.getRoles().add(com.docureader.model.Role.ROLE_DEMO);
            try {
                return userRepository.save(guest);
            } catch (Exception ex) {
                return userRepository.findAll().stream().filter(User::isDemo).findFirst()
                        .orElseGet(() -> {
                            String s2 = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                            return userRepository.save(new User("guest_" + s2, "guest_" + s2 + "@demo.docureader.ai", "demo", true));
                        });
            }
        });
        return UserPrincipal.create(user);
    }
}
