package com.example.attendance.service;

import com.example.attendance.entity.Attendance;
import com.example.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;

    public AttendanceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public List<Attendance> getAttendanceList(String userId) {
        return attendanceRepository.findByUserIdOrderByWorkDateAsc(userId);
    }

    public void punchIn(String userId) {
        LocalDate today = LocalDate.now();
        Optional<Attendance> existing = attendanceRepository.findByUserIdAndWorkDate(userId, today);

        if (existing.isEmpty()) {
            Attendance attendance = new Attendance();
            attendance.setUserId(userId);
            attendance.setWorkDate(today);
            attendance.setClockIn(LocalDateTime.now());
            attendanceRepository.save(attendance);
        }
    }

    public void punchOut(String userId) {
        LocalDate today = LocalDate.now();
        Optional<Attendance> attendanceOpt = attendanceRepository.findByUserIdAndWorkDate(userId, today);

        if (attendanceOpt.isPresent()) {
            Attendance attendance = attendanceOpt.get();
            if (attendance.getClockOut() == null) {
                attendance.setClockOut(LocalDateTime.now());
                attendanceRepository.save(attendance);
            }
        }
    }
}
