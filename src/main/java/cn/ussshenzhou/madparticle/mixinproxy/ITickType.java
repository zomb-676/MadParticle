package cn.ussshenzhou.madparticle.mixinproxy;

import cn.ussshenzhou.madparticle.particle.TakeOver;

/**
 * @author USS_Shenzhou
 */
public interface ITickType {

    TakeOver.TickType getTickType();

    void setTickType(TakeOver.TickType tickType);
}
