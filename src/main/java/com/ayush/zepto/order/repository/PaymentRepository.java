package com.ayush.zepto.order.repository;

import com.ayush.zepto.order.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {}
