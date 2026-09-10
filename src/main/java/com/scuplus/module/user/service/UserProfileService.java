package com.scuplus.module.user.service;

import com.scuplus.module.user.dto.ProfileUpdateRequest;
import com.scuplus.module.user.dto.UserProfileVO;

public interface UserProfileService {

    /** 查看任意用户的个人详情 */
    UserProfileVO getProfile(Long userId);

    /** 更新自己的资料（昵称/手机号/头像），权限已在 Controller 校验 */
    void updateProfile(Long userId, ProfileUpdateRequest request);
}