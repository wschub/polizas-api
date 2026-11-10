package com.davivienda.demoti.shared.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			@Value("${seguridad.api-key}") String apiKey,
			ObjectMapper objectMapper)
			throws Exception {
		return http
				// API sin navegador ni sesion: CSRF, formulario y basic no aportan nada
				// y solo agregarian respuestas que el cliente no espera.
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.sessionManagement(
						sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// Quien autoriza es ApiKeyFilter, no estas reglas.
				.authorizeHttpRequests(peticiones -> peticiones.anyRequest().permitAll())
				.addFilterBefore(
						new ApiKeyFilter(apiKey, objectMapper), UsernamePasswordAuthenticationFilter.class)
				.build();
	}

	/**
	 * Gestor de usuarios vacio, declarado solo para que
	 * {@code UserDetailsServiceAutoConfiguration} se retire y deje de crear un
	 * usuario con contraseña aleatoria, que ademas se imprime en el log al
	 * arrancar. Aqui no hay usuarios: la autenticacion es la API key.
	 */
	@Bean
	UserDetailsService userDetailsService() {
		return new InMemoryUserDetailsManager();
	}
}
