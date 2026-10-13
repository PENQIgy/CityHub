package com.cityhub.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 个人资料更新入参：昵称 / 头像落在 tb_user，其余落在 tb_user_info。
 *
 * @author CityHub
 */
@Data
public class UserProfileDTO {
    private String nickName;
    private String icon;
    private String city;
    private String introduce;
    private Boolean gender;
    private LocalDate birthday;
}
