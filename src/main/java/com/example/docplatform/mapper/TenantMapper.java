package com.example.docplatform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.docplatform.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {}
