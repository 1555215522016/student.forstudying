package com.scuplus.module.user.service.impl;

import com.scuplus.common.exception.BusinessException;
import com.scuplus.common.exception.ErrorCode;
import com.scuplus.module.user.dto.ProfileUpdateRequest;
import com.scuplus.module.user.dto.UserProfileVO;
import com.scuplus.module.user.entity.User;
import com.scuplus.module.user.mapper.UserMapper;
import com.scuplus.module.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final UserMapper userMapper;

    @Override
    public UserProfileVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return toVO(user);
    }

    @Override
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        User patch = new User();
        patch.setId(userId);
        if (request.getNickname() != null) {
            patch.setNickname(request.getNickname().trim());
        }
        if (request.getPhone() != null) {
            patch.setPhone(request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null) {
            patch.setAvatarUrl(request.getAvatarUrl());
        }
        userMapper.updateById(patch); // MyBatis-Plus 只更新非 null 字段
        log.info("用户 {} 更新了个人资料（昵称/手机号/头像）", userId);
    }

    private UserProfileVO toVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setName(user.getName());
        vo.setGender(genderText(user.getGender()));
        vo.setBirthday(user.getBirthday());
        vo.setPhone(user.getPhone());
        vo.setMajor(user.getMajor());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }

    /** 性别：DB 存 0未知/1男/2女，对外统一转成中文，前端不用再映射 */
    private String genderText(Integer gender) {
        if (gender == null) {
            return "未知";
        }
        return switch (gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> "未知";
        };
    }
}