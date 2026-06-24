package com.edumentor.auth.service;

import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user;
        try {
            UUID uuid = UUID.fromString(username);
            user = userRepository.findById(uuid)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        } catch (IllegalArgumentException e) {
            user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UsernameNotFoundException("账号已被禁用");
        }

        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new org.springframework.security.core.userdetails.User(
            user.getId().toString(),
            user.getPasswordHash(),
            authorities
        );
    }
}
