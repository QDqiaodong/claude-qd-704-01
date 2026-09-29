package com.fire.protect.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fire.protect.domain.SysUser;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    List<SysUser> findAllByOrderByIdAsc();

    List<SysUser> findByRoleOrderByIdAsc(String role);
}
