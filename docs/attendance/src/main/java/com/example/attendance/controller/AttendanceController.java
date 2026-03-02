package com.example.attendance.controller;

import com.example.attendance.entity.Attendance;
import com.example.attendance.entity.User;
import com.example.attendance.repository.AttendanceRepository;
import com.example.attendance.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class AttendanceController {

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final String WEATHER_API_KEY = "5ec9752fda5cfd0bb70a5858f84af75c";

    public AttendanceController(AttendanceRepository attendanceRepository, UserRepository userRepository) {
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/")
    public String index(Principal principal, HttpSession session) {
        if (principal == null)
            return "redirect:/login";

        User loginUser = userRepository.findById(principal.getName()).orElse(null);
        session.setAttribute("loginUser", loginUser);

        if (loginUser != null && "ADMIN".equals(loginUser.getRole())) {
            return "redirect:/admin/menu";
        }
        return "redirect:/attendance";
    }

    @GetMapping("/attendance")
    public String attendancePage(
            @RequestParam(name = "year", required = false) Integer year,
            @RequestParam(name = "month", required = false) Integer month,
            HttpSession session, Model model) {

        User loginUser = (User) session.getAttribute("loginUser");
        if (loginUser == null)
            return "redirect:/login";

        YearMonth targetMonth = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
        model.addAttribute("targetMonth", targetMonth);
        model.addAttribute("prevMonth", targetMonth.minusMonths(1));
        model.addAttribute("nextMonth", targetMonth.plusMonths(1));

        List<Attendance> dbAttendances = attendanceRepository.findByUserIdOrderByWorkDateAsc(loginUser.getUserId());
        Map<LocalDate, Attendance> attendanceMap = dbAttendances.stream()
                .collect(Collectors.toMap(Attendance::getWorkDate, a -> a, (a1, a2) -> a1));

        List<AttendanceDisplayDto> displayList = new ArrayList<>();
        LocalDate startOfMonth = targetMonth.atDay(1);
        int emptyCells = startOfMonth.getDayOfWeek().getValue() - 1;
        for (int i = 0; i < emptyCells; i++)
            displayList.add(null);
        for (LocalDate date = startOfMonth; !date.isAfter(targetMonth.atEndOfMonth()); date = date.plusDays(1)) {
            displayList.add(new AttendanceDisplayDto(date, attendanceMap.get(date), isJapaneseHoliday(date)));
        }

        fetchWeatherData(model);
        model.addAttribute("user", loginUser);
        model.addAttribute("displayList", displayList);
        model.addAttribute("today", LocalDate.now());

        return "attendance";
    }

    @GetMapping("/admin/menu")
    public String adminMenu(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"ADMIN".equals(user.getRole()))
            return "redirect:/attendance";
        return "admin_menu";
    }

    @GetMapping("/admin/summary")
    public String adminSummary(@RequestParam(name = "userId", required = false) String searchUserId,
            HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"ADMIN".equals(user.getRole()))
            return "redirect:/attendance";

        List<Attendance> list = (searchUserId != null && !searchUserId.isEmpty())
                ? attendanceRepository.findByUserIdOrderByWorkDateAsc(searchUserId)
                : attendanceRepository.findAll();

        model.addAttribute("allAttendance", list);
        return "admin_summary";
    }

    @GetMapping("/admin/users")
    public String userList(HttpSession session, Model model) {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"ADMIN".equals(user.getRole()))
            return "redirect:/attendance";
        model.addAttribute("users", userRepository.findAll());
        return "admin_users";
    }

    @PostMapping("/admin/users/save")
    public String saveUser(@RequestParam String userId, @RequestParam String name,
            @RequestParam String password, @RequestParam String role, HttpSession session) {
        User admin = (User) session.getAttribute("loginUser");
        if (admin == null || !"ADMIN".equals(admin.getRole()))
            return "redirect:/attendance";

        User u = new User();
        u.setUserId(userId);
        u.setName(name);
        u.setPassword(password);
        u.setRole(role);
        userRepository.save(u);
        return "redirect:/admin/users";
    }

    @PostMapping("/admin/users/delete")
    public String deleteUser(@RequestParam String userId, HttpSession session) {
        User admin = (User) session.getAttribute("loginUser");
        if (admin == null || !"ADMIN".equals(admin.getRole()))
            return "redirect:/attendance";
        userRepository.deleteById(userId);
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/download-csv")
    public void downloadCsv(HttpSession session, HttpServletResponse response) throws IOException {
        User user = (User) session.getAttribute("loginUser");
        if (user == null || !"ADMIN".equals(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String fileName = "勤怠データ_" + LocalDate.now() + ".csv";
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));

        try (PrintWriter writer = response.getWriter()) {
            writer.write('\ufeff');
            writer.println("ユーザーID,日付,出勤時刻,退勤時刻,メモ");

            List<Attendance> list = attendanceRepository.findAll();
            for (Attendance a : list) {
                writer.println(String.format("%s,%s,%s,%s,%s",
                        a.getUserId(), a.getWorkDate(),
                        a.getClockIn() != null ? a.getClockIn().toLocalTime().withNano(0) : "",
                        a.getClockOut() != null ? a.getClockOut().toLocalTime().withNano(0) : "",
                        a.getMemo() != null ? a.getMemo() : ""));
            }
        }
    }

    @PostMapping("/attendance/punch-in")
    public String punchIn(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user != null) {
            Attendance a = new Attendance();
            a.setUserId(user.getUserId());
            a.setWorkDate(LocalDate.now());
            a.setClockIn(LocalDateTime.now());
            attendanceRepository.save(a);
        }
        return "redirect:/attendance";
    }

    @PostMapping("/attendance/punch-out")
    public String punchOut(HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user != null) {
            attendanceRepository.findByUserIdAndWorkDate(user.getUserId(), LocalDate.now()).ifPresent(a -> {
                a.setClockOut(LocalDateTime.now());
                attendanceRepository.save(a);
            });
        }
        return "redirect:/attendance";
    }

    @PostMapping("/attendance/memo-save")
    public String saveMemo(@RequestParam("memo") String memo, HttpSession session) {
        User user = (User) session.getAttribute("loginUser");
        if (user != null) {
            attendanceRepository.findByUserIdAndWorkDate(user.getUserId(), LocalDate.now()).ifPresent(a -> {
                a.setMemo(memo);
                attendanceRepository.save(a);
            });
        }
        return "redirect:/attendance";
    }

    @SuppressWarnings("unchecked")
    private void fetchWeatherData(Model model) {
        try {
            String url = "https://api.openweathermap.org/data/2.5/weather?q=Tokyo&appid=" + WEATHER_API_KEY
                    + "&units=metric&lang=ja";
            Map<String, Object> resp = new RestTemplate().getForObject(url, Map.class);
            if (resp != null) {
                Map<String, Object> main = (Map<String, Object>) resp.get("main");
                List<Map<String, Object>> weather = (List<Map<String, Object>>) resp.get("weather");
                model.addAttribute("weatherEmoji",
                        ((String) weather.get(0).get("main")).contains("Clear") ? "☀️" : "☁️");
                model.addAttribute("weatherText", String.format("%s %.1f℃", weather.get(0).get("description"),
                        ((Number) main.get("temp")).doubleValue()));
            }
        } catch (Exception e) {
            model.addAttribute("weatherText", "天気取得待機中");
        }
    }

    private boolean isJapaneseHoliday(LocalDate date) {
        int m = date.getMonthValue();
        int d = date.getDayOfMonth();
        return (m == 1 && d == 1) || (m == 2 && (d == 11 || d == 23)) || (m == 5 && (d >= 3 && d <= 5));
    }

    public static class AttendanceDisplayDto {
        public LocalDate date;
        public Attendance data;
        public boolean isHoliday;
        public String workingHours;

        public AttendanceDisplayDto(LocalDate d, Attendance a, boolean h) {
            this.date = d;
            this.data = a;
            this.isHoliday = h;
            if (a != null && a.getClockIn() != null && a.getClockOut() != null) {
                long mins = Duration.between(a.getClockIn(), a.getClockOut()).toMinutes();
                this.workingHours = String.format("%.1fh", mins / 60.0);
            } else {
                this.workingHours = "";
            }
        }
    }
}
