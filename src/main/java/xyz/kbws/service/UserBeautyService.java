package xyz.kbws.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kbws.model.dto.userBeauty.UserBeautyAddDTO;
import xyz.kbws.model.dto.userBeauty.UserBeautyQuery;
import xyz.kbws.model.entity.UserBeauty;

/**
 * @author hsy
 * @description 针对表【user_beauty(靓号表)】的数据库操作Service
 * @createDate 2024-04-24 14:40:17
 */
public interface UserBeautyService extends IService<UserBeauty> {

    void saveUserBeauty(UserBeautyAddDTO userBeautyAddDTO);

    QueryWrapper<UserBeauty> getQueryWrapper(UserBeautyQuery userBeautyQuery);

}
