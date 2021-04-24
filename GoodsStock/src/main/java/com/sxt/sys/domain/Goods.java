package com.sxt.sys.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * <p>
 *
 * </p>
 * 物料信息实体
 *
 * @author hayes
 * @since 2021-04-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("materiel_info")
@ToString
public class Goods implements Serializable {

    private static final long serialVersionUID = 1L;

//    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    private String materielCode;
    private String materielName;
    private String materielModel;
    private String materielBrand;
    private String unit;
    private String demandType;
    private String demandDate;
    private Integer materielNumber;
    private String deliveryDate;
    private Integer untaxedOffer;
    //数据状态：1-正常 2-出仓历史 3-入仓历史 5-报价历史
    private String dataStatus;
    private Date createTime;

}
