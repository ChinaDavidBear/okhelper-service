package com.ok.okhelper.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.ok.okhelper.common.PageModel;
import com.ok.okhelper.common.ServerResponse;
import com.ok.okhelper.dao.RoleMapper;
import com.ok.okhelper.dao.StoreMapper;
import com.ok.okhelper.dao.UserMapper;
import com.ok.okhelper.exception.ConflictException;
import com.ok.okhelper.exception.IllegalException;
import com.ok.okhelper.pojo.bo.RoleBo;
import com.ok.okhelper.pojo.bo.UserBo;
import com.ok.okhelper.pojo.constenum.ConstEnum;
import com.ok.okhelper.pojo.dto.UserAndStoreDto;
import com.ok.okhelper.pojo.dto.UserDto;
import com.ok.okhelper.pojo.po.Role;
import com.ok.okhelper.pojo.po.Store;
import com.ok.okhelper.pojo.po.User;
import com.ok.okhelper.pojo.vo.EmployeeVo;
import com.ok.okhelper.pojo.vo.UserVo;
import com.ok.okhelper.service.PermissionService;
import com.ok.okhelper.service.UserService;
import com.ok.okhelper.shiro.JWTUtil;
import com.ok.okhelper.until.PasswordHelp;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthenticatedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/*
 *Author:zhangxin_an,zc
 *Description:
 *Data:Created in 21:27 2018/4/10
 */
@Service
public class UserServiceImpl implements UserService {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private StoreMapper storeMapper;

    @Autowired
    @Lazy
    private PermissionService permissionService;


    @Override
    public User findUserByUserNme(String username) {
        return userMapper.findUserByUserName(username);
    }

    /**
    * @Author zhangxin_an
    * @Date 2018/4/25 8:54
    * @Params [userName, password, ip]
    * @Return com.ok.okhelper.common.ServerResponse
    * @Description:员工登陆
    */
    @Override
    public ServerResponse loginUser(String userName, String password, String ip) {

        if (StringUtils.isBlank(userName) || StringUtils.isBlank(password)) {
            throw new AuthenticationException("用户名或密码为空");
        }

        User user = findUserByUserNme(userName);


        if (user == null) {
            throw new AuthenticationException("用户名不存在");
        }

        if (user.getDeleteStatus().equals("0")) {
            throw new UnauthenticatedException("用户状态异常");
        }

        //加密
        String inPassword = PasswordHelp.passwordSalt(userName, password).toString();

        String dbPassword = user.getUserPassword();
        if (!dbPassword.equals(inPassword)) {
            throw new AuthenticationException("密码不正确");
        }

//        Long userId = user.getId();

        user.setLastLoginIp(ip);
        userMapper.updateByPrimaryKeySelective(user);


        UserVo userVo = getUser(user);


        return ServerResponse.createBySuccess(userVo);
    }


    private UserVo getUser(User user) {
        //传值给前端封装类
        UserVo userVo = new UserVo();
        Long userId = user.getId();
        BeanUtils.copyProperties(user, userVo);
        List<Role> roles = roleMapper.findRoleByUserId(user.getId());
        if (!CollectionUtils.isEmpty(roles)) {
            userVo.setRoleList(roles);
        }

        //获取用户权限
//        List<Permission> permissionList = permissionnService.findAddPermission(userId);
//
//        if (!CollectionUtils.isEmpty(permissionList)) {
//            userVo.setPermissionList(permissionList);
//        }

        String token = JWTUtil.sign(userId, user.getUserName(), user.getUserPassword(), user.getStoreId());

        userVo.setToken(token);
        return userVo;
    }

    /*
     * @Author zhangxin_an
     * @Date 2018/4/14 18:52
     * @Params [userDto]
     * @Return com.ok.okhelper.common.ServerResponse
     * @Description:店长注册
     */
    @Override
    @Transactional
    public void userRegister(UserAndStoreDto userAndStoreDto) {
//        logger.info("Enter method userRegister" + userAndStoreDto);
        if (StringUtils.isBlank(userAndStoreDto.getUserName())
                || StringUtils.isBlank(userAndStoreDto.getUserPassword())
                || StringUtils.isBlank(userAndStoreDto.getStoreName())
                || StringUtils.isBlank(userAndStoreDto.getStorePhone())
                )
        {
            throw new IllegalException("注册信息不完善（用户名，密码,店铺信息不能为空）");
        }

        //密码加密
        String secret = PasswordHelp.passwordSalt(userAndStoreDto.getUserName(), userAndStoreDto.getUserPassword());
        userAndStoreDto.setUserPassword(secret);


        User user = new User();
        BeanUtils.copyProperties(userAndStoreDto, user);
        user.setUserPhone(user.getUserName());

        Store store = new Store();
        BeanUtils.copyProperties(userAndStoreDto, store);


        try {
            userMapper.insertSelective(user);
            Long userId = user.getId();
            store.setLeaderId(userId);
            store.setOperator(userId);
            storeMapper.insertSelective(store);
            user.setStoreId(store.getId());
            userMapper.updateByPrimaryKeySelective(user);
            Long roleId = (long) ConstEnum.ROLE_STOREMANAGER.getCode();
            roleMapper.insertUserRole(userId, roleId);

        } catch (Exception e) {
            throw new RuntimeException("注册失败");
        }


//        logger.info("Exit method userRegister" );


    }

    @Override
    public ServerResponse checkUserName(String userName) {

        if (StringUtils.isBlank(userName)) {
            throw new IllegalException("用户名为空");
        }

        if (CollectionUtils.isEmpty(userMapper.checkUserName(userName))) {
            return ServerResponse.createBySuccess();
        }
        throw new ConflictException("用户名重复");
    }

    /*
     * @Author zhangxin_an
     * @Date 2018/4/15 10:40
     * @Params [token]
     * @Return com.ok.okhelper.common.ServerResponse
     * @Description:
     */
    @Override
    public ServerResponse getUserListByStoreId(String token) {
        Long userId = JWTUtil.getUserId(token);
        Long storeId = JWTUtil.getStoreId(token);
        List<Long> userIdList = userMapper.getUserListByStoreId(storeId);

        if (CollectionUtils.isEmpty(userIdList)) {
            return ServerResponse.createBySuccess("无员工数据", null);
        }

        userIdList.remove(userId);
        List<UserVo> userVos = new ArrayList<>();
        userIdList.forEach(id -> {
            UserVo userVo = new UserVo();

            //获取用户
            User user = userMapper.selectByPrimaryKey(id);
            if (user == null) {
                throw new IllegalException("用户不存在");
            }


            //获取用户角色
            List<Role> roles = roleMapper.findRoleByUserId(user.getId());
            if (!CollectionUtils.isEmpty(roles)) {
                userVo.setRoleList(roles);
            }
            BeanUtils.copyProperties(user, userVo);
            userVo.setRoleList(roles);

            userVos.add(userVo);
        });

        return ServerResponse.createBySuccess(userVos);
    }

    /*
     * @Author zhangxin_an
     * @Date 2018/4/17 20:31
     * @Params [userDto]
     * @Return com.ok.okhelper.common.ServerResponse
     * @Description:增加员工
     */
    @Override
    public ServerResponse addEmployee(UserDto userDto) {

        logger.info("Enter method addEmployee" + userDto);

        Long userId = JWTUtil.getUserId();
        Long storeId = JWTUtil.getStoreId();

        if (userId == null || storeId == null) {
            throw new IllegalException("用户Id或店铺Id为空");
        }
        if (StringUtils.isBlank(userDto.getUserName())
                || StringUtils.isBlank(userDto.getUserPassword())
                ) {
            throw new IllegalException("添加员工信息不完善（用户名，密码不为空）");
        }

        //密码加密
        userDto.setUserPassword(PasswordHelp.passwordSalt(userDto.getUserName(), userDto.getUserPassword()));


        User user = new User();
        BeanUtils.copyProperties(userDto, user);
        user.setStoreId(storeId);
        user.setOperator(userId);


        try {

            userMapper.insertSelective(user);
        } catch (Exception e) {
            throw new IllegalException("注册失败");
        }


        ServerResponse serverResponse = ServerResponse.createBySuccess("添加成功");

        logger.info("Exit method addEmployee" + serverResponse);

        return serverResponse;
    }


    /**
     * @Author zc
     * @Date 2018/4/18 下午2:09
     * @Param [userAndRoleDto]
     * @Return com.ok.okhelper.common.ServerResponse
     * @Description: 变更用户角色
     */
    @Transactional
    public ServerResponse changeRole(Long employeeId, List<Long> roles) {

        //当前操作者的storeId
        Long storeId = JWTUtil.getStoreId();

        User employee = userMapper.selectByPrimaryKey(employeeId);

        if (null == employee) {
            throw new IllegalException("无此用户");
        }

        if (ObjectUtils.notEqual(employee.getStoreId(), storeId)) {
            throw new AuthorizationException("无权修改");
        }

        userMapper.deleteAllRoleFromUser(employeeId);

        if (CollectionUtils.isNotEmpty(roles)) {
            roles.forEach(roleId -> {
                userMapper.insertRoleToUser(employeeId, roleId, JWTUtil.getUserId());
            });
        }

        //更新用户权限缓存
        permissionService.updatePermissionCacheByUserId(employeeId);

        return ServerResponse.createBySuccessMessage("权限变更成功");
    }
    /*
   * @Author zhangxin_an
   * @Date 2018/4/19 17:38
   * @Params []
   * @Return java.util.List<com.ok.okhelper.pojo.vo.EmployeeVo>
   * @Description:获取员工
   */
    @Override
    public PageModel<EmployeeVo> getEmployeeList(PageModel pageModel) {
        
        logger.info("Enter method getEmployeeList()");
        //启动分页
        PageHelper.startPage(pageModel.getPageNum(), pageModel.getLimit());
        
        
        
        //获取当前登陆者的Id和店铺Id
        Long storeId = JWTUtil.getStoreId();
        Long bossId = JWTUtil.getUserId();
        
        if( null == storeId || null == bossId){
            throw  new UnauthenticatedException("登陆异常");
        }
        
        List<UserBo> userBos = userMapper.getEmployeeList(storeId);
        
        if(CollectionUtils.isEmpty(userBos)){
            return null;
        }
        
        List<EmployeeVo> employeeVoList = new ArrayList<>();
        
        
        userBos.forEach(userBo->{
            //前端数据
            EmployeeVo employeeVo = new EmployeeVo();
            
            List<RoleBo> roleBos = new ArrayList<>(1);
    
            BeanUtils.copyProperties(userBo,employeeVo);
            List<Role> roles = roleMapper.findRoleByUserId(userBo.getId());
            if( !CollectionUtils.isEmpty(roles)){
                roles.forEach(r->{
                    RoleBo roleBo = new RoleBo();
                    BeanUtils.copyProperties(r,roleBo);
                    roleBos.add(roleBo);
                });
                employeeVo.setRoleList(roleBos);
            }
            
            
            
            employeeVoList.add(employeeVo);
        });
    
    
    
        PageInfo<EmployeeVo> pageInfo = new PageInfo<>(employeeVoList);
    
        logger.info("Exit method getEmployeeList()"+pageInfo);
    
        return PageModel.convertToPageModel(pageInfo);
    
    }
}
