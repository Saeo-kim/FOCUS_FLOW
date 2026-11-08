package com.example.project_flow.controller;

import com.example.project_flow.dto.AttendanceDtos.CheckOutRequest;
import com.example.project_flow.dto.AttendanceDtos.HistoryResponse;
import com.example.project_flow.dto.AttendanceDtos.StatusResponse;
import com.example.project_flow.service.AttendanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public StatusResponse status() {
        return service.status();
    }

    @PostMapping("/check-in")
    public StatusResponse checkIn() {
        return service.checkIn();
    }

    @PostMapping("/check-out")
    public StatusResponse checkOut(@RequestBody(required = false) CheckOutRequest request) {
        return service.checkOut(request != null ? request.at() : null);
    }

    @PostMapping("/break/start")
    public StatusResponse startBreak() {
        return service.startBreak();
    }

    @PostMapping("/break/end")
    public StatusResponse endBreak() {
        return service.endBreak();
    }

    @GetMapping("/history")
    public HistoryResponse history(@RequestParam(required = false) YearMonth month) {
        return service.history(month);
    }
}
