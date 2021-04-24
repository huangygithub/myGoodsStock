package com.sxt.sys.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sxt.sys.Vo.GoodsVo;
import com.sxt.sys.common.DataGridView;
import com.sxt.sys.common.ResultObj;
import com.sxt.sys.domain.Goods;
import com.sxt.sys.service.GoodsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @since 2021-04-09
 */
@RestController
@RequestMapping("/goods")
public class GoodsController {
    @Autowired
    private GoodsService goodsService;


    /**
     * 用户全查询
     */
    @RequestMapping("loadAllGoods")
    public DataGridView loadAllGoods(GoodsVo goodsVo) {
        if (StringUtils.isBlank(goodsVo.getDataStatus())) {
            goodsVo.setDataStatus("1");
        }
        IPage<Goods> page = new Page<Goods>(goodsVo.getPage(), goodsVo.getLimit());
        QueryWrapper<Goods> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(goodsVo.getMaterielName()), "materiel_name", goodsVo.getMaterielName());
        queryWrapper.like(StringUtils.isNotBlank(goodsVo.getMaterielCode()), "materiel_code", goodsVo.getMaterielCode());
        queryWrapper.like(StringUtils.isNotBlank(goodsVo.getMaterielModel()), "materiel_model", goodsVo.getMaterielModel());
        queryWrapper.eq(StringUtils.isNotBlank(goodsVo.getDataStatus()), "data_status", goodsVo.getDataStatus());
        this.goodsService.page(page, queryWrapper);

        System.out.println(goodsService.getClass().getSimpleName());
        List<Goods> list = page.getRecords();
        return new DataGridView(page.getTotal(), list);

    }

    /**
     * 根据用户ID查询用户信息
     *
     * @param id
     * @return
     */
    @RequestMapping("loadGoodsByUId")
    public Goods loadGoodsByUId(Integer id) {
        QueryWrapper<Goods> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id);
        Goods goods = this.goodsService.getOne(queryWrapper);
        return goods;
    }
//
//    private String generateUuid() {
//        String uuid = UUID.randomUUID().toString();
//        uuid = uuid.replace("-", "");
//        return uuid;
//    }

    /**
     * 添加物料
     */
    @RequestMapping("addGoods")
    public ResultObj addGoods(GoodsVo goodsVo) {
        try {
            goodsVo.setDataStatus("1");
            goodsVo.setCreateTime(new Date());
            this.goodsService.save(goodsVo);
            return ResultObj.ADD_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.ADD_ERROR;
        }
    }

    /**
     * 修改物料
     */
    @RequestMapping("updateGoods")
    public ResultObj updateGoods(GoodsVo goodsVo) {
        try {
            this.goodsService.updateById(goodsVo);
            return ResultObj.UPDATE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.UPDATE_ERROR;
        }
    }

    /**
     * 删除物料
     */
    @RequestMapping("deleteGoods")
    public ResultObj deleteGoods(Integer id) {
        try {
            this.goodsService.removeById(id);
            return ResultObj.DELETE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.DELETE_ERROR;
        }
    }

    /**
     * 生成报价历史
     */
    @RequestMapping("offer")
    public ResultObj addOffer(GoodsVo goodsVo) {
        try {
            // 新增报价历史
            goodsVo.setDataStatus("5");
            goodsVo.setCreateTime(new Date());
            this.goodsService.save(goodsVo);
            return ResultObj.OFFER_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.OFFER_ERROR;
        }
    }
    /**
     * 生成入仓历史
     */
    @RequestMapping("inHouse")
    public ResultObj addInHouse(GoodsVo goodsVo) {
        try {
            Integer oldId = goodsVo.getId();

            // 保存入仓历史
            goodsVo.setDataStatus("3");
            goodsVo.setId(null);
            goodsVo.setCreateTime(new Date());
            this.goodsService.save(goodsVo);

            QueryWrapper<Goods> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("id", oldId);
            Goods goods = this.goodsService.getOne(queryWrapper);
            // 入仓加库存
            Integer newMaterielNumber = goods.getMaterielNumber() + goodsVo.getMaterielNumber();

            // 更新自己的库存
            goods.setMaterielNumber(newMaterielNumber);
            this.goodsService.updateById(goods);
            return ResultObj.INHOUSE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.INHOUSE_ERROR;
        }
    }
    /**
     * 生成出仓历史
     */
    @RequestMapping("outHouse")
    public ResultObj addOutHouse(GoodsVo goodsVo) {
        try {

            Integer oldId = goodsVo.getId();
            QueryWrapper<Goods> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("id", oldId);
            Goods goods = this.goodsService.getOne(queryWrapper);
            // 出仓减库存
            Integer newMaterielNumber = goods.getMaterielNumber() - goodsVo.getMaterielNumber();
            if (newMaterielNumber < 0) {
                return ResultObj.OUTHOUSE_WARN;
            }

            // 保存入仓历史
            goodsVo.setDataStatus("2");
            goodsVo.setId(null);
            goodsVo.setCreateTime(new Date());
            this.goodsService.save(goodsVo);

            // 更新自己的库存
            goods.setMaterielNumber(newMaterielNumber);
            this.goodsService.updateById(goods);

            return ResultObj.OUTHOUSE_SUCCESS;
        } catch (Exception e) {
            e.printStackTrace();
            return ResultObj.OUTHOUSE_ERROR;
        }
    }
}
