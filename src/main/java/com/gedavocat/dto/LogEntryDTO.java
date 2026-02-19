package com.gedavocat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour une ligne de log
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogEntryDTO {
    private LocalDateTime timestamp;
    private String level;
    private String logger;
    private String message;
    private String thread;
    private String exception;
}
