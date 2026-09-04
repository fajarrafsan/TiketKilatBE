package com.projekan.tiket_pesawat.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.projekan.tiket_pesawat.models.Role;
import com.projekan.tiket_pesawat.models.User;
import com.projekan.tiket_pesawat.repository.UserRepository;
import com.projekan.tiket_pesawat.services.CustomUserDetailsServiceImpl;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceImplTest {
    private static final String EMAIL = "traveler@example.test";

    @Mock
    private UserRepository userRepository;

    @ParameterizedTest
    @EnumSource(Role.class)
    void mapsDatabaseRoleToRawAuthorityWithoutRolePrefix(Role role) {
        User user = User.builder().email(EMAIL).password("stored-password-hash").role(role).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        CustomUserDetailsServiceImpl service = new CustomUserDetailsServiceImpl(userRepository);

        UserDetails userDetails = service.loadUserByUsername(EMAIL);

        assertEquals(EMAIL, userDetails.getUsername());
        assertEquals("stored-password-hash", userDetails.getPassword());
        assertEquals(List.of(role.name()), userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void missingDatabaseUserIsNotAuthenticated() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        CustomUserDetailsServiceImpl service = new CustomUserDetailsServiceImpl(userRepository);

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(EMAIL));
    }
}
