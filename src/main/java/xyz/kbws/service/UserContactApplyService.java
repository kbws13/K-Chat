package xyz.kbws.service;

import com.baomidou.mybatisplus.extension.service.IService;
import xyz.kbws.model.entity.UserContactApply;
import xyz.kbws.model.vo.UserContactApplyVO;

import java.util.List;

/**
* @author hsy
* @description 针对表【user_contact_apply(联系人申请表)】的数据库操作Service
* @createDate 2024-04-26 14:51:59
*/
public interface UserContactApplyService extends IService<UserContactApply> {

    List<UserContactApplyVO> getUserContactApplyVO(String receiveId);

    /**
     * 处理申请
     * @param userId 用户id
     * @param applyId 申请记录id
     * @param status 用户对该次申请的操作
     */
    void dealWithApply(String userId, Integer applyId, Integer status);
}
