package com.example.attendance;

import com.example.attendance.entity.User;
import com.example.attendance.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AttendanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AttendanceApplication.class, args);
	}

	@SuppressWarnings("deprecation")
	@Bean
	public PasswordEncoder passwordEncoder() {
		return NoOpPasswordEncoder.getInstance();
	}

	@Bean
	CommandLineRunner init(UserService userService) {
		return args -> {
			if (userService.findByUserId("admin") == null) {
				User admin = new User();
				admin.setUserId("admin");
				admin.setPassword("password");
				admin.setName("システム管理者");
				admin.setRole("ADMIN");
				userService.register(admin);
			}
			if (userService.findByUserId("user01") == null) {
				User user = new User();
				user.setUserId("user01");
				user.setPassword("pass123");
				user.setName("木下 優空");
				user.setRole("USER");
				userService.register(user);
			}
			System.out.println("--- ユーザー作成完了: admin/password, user01/pass123 ---");
		};
	}
}
