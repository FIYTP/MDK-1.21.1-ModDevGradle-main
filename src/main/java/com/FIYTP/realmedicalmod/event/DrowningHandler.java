package com.FIYTP.realmedicalmod.event;

/**
 * 溺水伤害处理类已整合至 {@link HitPartHandler} 与生理循环系统。
 * 原版溺水伤害在 HitPartHandler 中统一被拦截清零 (setNewDamage(0.0f))，
 * 玩家水下状态由 MedicalMainLoop 检测并驱动 RespirationModule (呼吸置0)
 * 与 BloodOxygenModule (按生理梯度损耗及浮点精细恢复) 自然完成生理演进。
 */
public class DrowningHandler {
    // 逻辑已收敛至 HitPartHandler 与 RespirationModule/BloodOxygenModule
}
