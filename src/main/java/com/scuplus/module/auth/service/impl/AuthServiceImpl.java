package com.scuplus.module.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.common.security.LoginUser;
import com.scuplus.module.auth.service.AuthService;
import com.scuplus.module.user.entity.User;
import com.scuplus.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现
 *
 * @RequiredArgsConstructor + final 字段 = 构造器注入
 * 比 @Autowired 字段注入好：依赖可见、易测试（面试点）
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Demo 阶段写死的测试账号（真实需对接学校统一认证） */
    private static final String TEST_STUDENT_ID = "2022001";
    private static final String TEST_PASSWORD = "abc123";

    private final UserMapper userMapper;

    @Override
    public LoginUser login(String studentId, String password) {
        // 1. 模拟学校统一认证（Demo）
        mockSchoolAuth(studentId, password);

        // 2. 按学号查用户，不存在则自动建档（首次登录即注册）
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentId, studentId));
        if (user == null) {
            user = createUser(studentId);
        }

        // 3. 组装登录快照（存 session 用）
        return new LoginUser(user.getId(), user.getStudentId(),
                user.getNickname(), user.getAvatarUrl(), user.getRole());
    }

    @Override
    public LoginUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginUser) {
            return (LoginUser) auth.getPrincipal();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 模拟学校统一认证
     * Demo：任意学号 + TEST_PASSWORD 即可通过，首次登录自动建档。
     * 放宽学号校验是为了让 JMeter 能用 2022001~2022999 模拟多用户并发抢课（单账号会被幂等挡住，压不出并发）。
     * TODO: 对接真实学校系统（https / 加密参数 / 可能验证码）
     */
    private void mockSchoolAuth(String studentId, String password) {
        if (studentId == null || studentId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "学号不能为空");
        }
        if (!TEST_PASSWORD.equals(password)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "学号或密码错误");
        }
    }

    /** 首次登录自动建档：学号必须有，昵称头像等资料后续补充 */
    private User createUser(String studentId) {
        User user = new User();
        user.setStudentId(studentId);
        userMapper.insert(user);
        return user;
    }
}
