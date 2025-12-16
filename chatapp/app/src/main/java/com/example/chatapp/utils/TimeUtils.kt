package com.example.chatapp.utils

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun formatTimeAgo(isoString: String): String {
    return try {
        // Parse ISO string, có thể có hoặc không có timezone
        val instant = if (isoString.contains("+") || isoString.endsWith("Z")) {
            Instant.parse(isoString)
        } else {
            // Nếu không có timezone, thêm Z để parse như UTC
            Instant.parse(isoString + "Z")
        }
        
        val now = Instant.now()
        val diff = ChronoUnit.SECONDS.between(instant, now)
        
        // Đảm bảo diff là số dương
        val absDiff = kotlin.math.abs(diff)
        
        when {
            absDiff < 60 -> "Vừa xong"
            absDiff < 3600 -> "${absDiff / 60} phút trước"
            absDiff < 86400 -> "${absDiff / 3600} giờ trước"
            absDiff < 604800 -> "${absDiff / 86400} ngày trước"
            else -> {
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.systemDefault())
                formatter.format(instant)
            }
        }
    } catch (e: Exception) {
        // Nếu parse lỗi, thử format lại từ ISO string
        try {
            val instant = Instant.parse(isoString.replace("+00:00", "Z"))
            val now = Instant.now()
            val diff = ChronoUnit.SECONDS.between(instant, now)
            val absDiff = kotlin.math.abs(diff)
            
            when {
                absDiff < 60 -> "Vừa xong"
                absDiff < 3600 -> "${absDiff / 60} phút trước"
                absDiff < 86400 -> "${absDiff / 3600} giờ trước"
                absDiff < 604800 -> "${absDiff / 86400} ngày trước"
                else -> {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                        .withZone(ZoneId.systemDefault())
                    formatter.format(instant)
                }
            }
        } catch (e2: Exception) {
            // Nếu vẫn lỗi, chỉ hiển thị ngày tháng từ string
            try {
                val datePart = isoString.substring(0, 10) // Lấy phần YYYY-MM-DD
                val parts = datePart.split("-")
                if (parts.size == 3) {
                    "${parts[2]}/${parts[1]}/${parts[0]}"
                } else {
                    isoString
                }
            } catch (e3: Exception) {
                isoString
            }
        }
    }
}





