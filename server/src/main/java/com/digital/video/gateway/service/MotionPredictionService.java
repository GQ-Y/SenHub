package com.digital.video.gateway.service;

import com.digital.video.gateway.driver.livox.model.Point;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运动预测服务
 * 用于预测目标的未来位置，包括滞空检测和落地位置预测
 */
public class MotionPredictionService {
    private static final Logger logger = LoggerFactory.getLogger(MotionPredictionService.class);

    // 重力加速度（米/秒²）
    private static final float GRAVITY = 9.8f;
    
    // 滞空判断阈值：Z方向速度小于此值且Z坐标高于此值，认为是滞空
    private static final float HOVERING_VELOCITY_THRESHOLD = 0.5f; // 米/秒
    private static final float HOVERING_HEIGHT_THRESHOLD = 0.5f;   // 米
    
    // 落地判断阈值：Z坐标低于此值，认为已落地
    private static final float GROUND_HEIGHT_THRESHOLD = 0.3f;     // 米

    /**
     * 目标运动状态
     */
    public enum MotionState {
        HOVERING,    // 滞空
        FALLING,     // 下落中
        LANDED,      // 已落地
        MOVING       // 正常移动
    }

    /**
     * 运动预测结果
     */
    public static class PredictionResult {
        public Point predictedPosition;      // 预测位置
        public MotionState motionState;      // 运动状态
        public Point landingPoint;          // 落地位置（如果在下落）
        public float timeToLand;            // 预计落地时间（秒）
        public float confidence;            // 预测置信度（0-1）

        public PredictionResult(Point predictedPosition, MotionState motionState) {
            this.predictedPosition = predictedPosition;
            this.motionState = motionState;
            this.confidence = 0.5f;
        }
    }

    /**
     * 检测目标运动状态
     * 
     * @param currentPosition 当前位置
     * @param velocity 当前速度 [vx, vy, vz]
     * @return 运动状态
     */
    public MotionState detectMotionState(Point currentPosition, float[] velocity) {
        float vz = velocity[2];
        float z = currentPosition.z;

        // 已落地：Z坐标低于阈值
        if (z < GROUND_HEIGHT_THRESHOLD) {
            return MotionState.LANDED;
        }

        // 滞空：Z方向速度很小且高度较高
        if (Math.abs(vz) < HOVERING_VELOCITY_THRESHOLD && z > HOVERING_HEIGHT_THRESHOLD) {
            return MotionState.HOVERING;
        }

        // 下落中：Z方向速度向下（负值）且高度较高
        if (vz < -0.3f && z > HOVERING_HEIGHT_THRESHOLD) {
            return MotionState.FALLING;
        }

        // 其他情况视为正常移动
        return MotionState.MOVING;
    }

    /**
     * 预测目标在未来某个时刻的位置
     * 
     * @param currentPosition 当前位置
     * @param velocity 当前速度 [vx, vy, vz]
     * @param futureTimeSeconds 未来时间（秒）
     * @return 预测位置
     */
    public Point predictPosition(Point currentPosition, float[] velocity, float futureTimeSeconds) {
        float vx = velocity[0];
        float vy = velocity[1];
        float vz = velocity[2];

        // 考虑重力加速度对Z方向的影响
        // 如果目标在下落，应用重力加速度
        MotionState state = detectMotionState(currentPosition, velocity);
        float effectiveVz = vz;
        
        if (state == MotionState.FALLING) {
            // 下落时，Z方向速度会因重力增加
            // v(t) = v0 + g*t
            effectiveVz = vz - GRAVITY * futureTimeSeconds;
        }

        // 预测位置：p(t) = p0 + v*t + 0.5*a*t^2
        float predictedX = currentPosition.x + vx * futureTimeSeconds;
        float predictedY = currentPosition.y + vy * futureTimeSeconds;
        float predictedZ;
        
        if (state == MotionState.FALLING) {
            // 下落时考虑重力：z(t) = z0 + v0*t - 0.5*g*t^2
            predictedZ = currentPosition.z + vz * futureTimeSeconds - 0.5f * GRAVITY * futureTimeSeconds * futureTimeSeconds;
            // 不能低于地面
            predictedZ = Math.max(predictedZ, GROUND_HEIGHT_THRESHOLD);
        } else {
            // 其他状态：简单线性预测
            predictedZ = currentPosition.z + effectiveVz * futureTimeSeconds;
        }

        return new Point(predictedX, predictedY, predictedZ);
    }

    /**
     * 预测目标落地位置和时间
     * 
     * @param currentPosition 当前位置
     * @param velocity 当前速度 [vx, vy, vz]
     * @return 预测结果，如果不在下落则返回null
     */
    public PredictionResult predictLanding(Point currentPosition, float[] velocity) {
        MotionState state = detectMotionState(currentPosition, velocity);
        
        if (state != MotionState.FALLING) {
            // 不在下落，返回当前位置的预测
            Point predictedPos = predictPosition(currentPosition, velocity, 0.5f);
            return new PredictionResult(predictedPos, state);
        }

        // 计算落地时间：z(t) = z0 + v0*t - 0.5*g*t^2 = 0
        // 解二次方程：-0.5*g*t^2 + v0*t + z0 = 0
        float vz = velocity[2];
        float z0 = currentPosition.z;
        
        // 如果vz已经是0或正数，说明不在下落
        if (vz >= 0) {
            Point predictedPos = predictPosition(currentPosition, velocity, 0.5f);
            return new PredictionResult(predictedPos, MotionState.HOVERING);
        }

        // 计算落地时间（考虑地面高度阈值）
        float targetZ = GROUND_HEIGHT_THRESHOLD;
        float deltaZ = z0 - targetZ;
        
        // 使用运动学公式：t = (v0 + sqrt(v0^2 + 2*g*deltaZ)) / g
        float discriminant = vz * vz + 2 * GRAVITY * deltaZ;
        if (discriminant < 0) {
            // 无解，返回当前位置
            Point predictedPos = predictPosition(currentPosition, velocity, 0.5f);
            return new PredictionResult(predictedPos, state);
        }
        
        float timeToLand = (-vz + (float) Math.sqrt(discriminant)) / GRAVITY;
        
        // 计算落地位置（X和Y方向）
        float vx = velocity[0];
        float vy = velocity[1];
        float landingX = currentPosition.x + vx * timeToLand;
        float landingY = currentPosition.y + vy * timeToLand;
        Point landingPoint = new Point(landingX, landingY, targetZ);

        // 预测PTZ到位时的位置（假设PTZ延迟0.5秒）
        float ptzDelaySeconds = 0.5f;
        Point predictedPosition;
        if (timeToLand > ptzDelaySeconds) {
            // 如果落地时间大于PTZ延迟，预测PTZ到位时的位置
            predictedPosition = predictPosition(currentPosition, velocity, ptzDelaySeconds);
        } else {
            // 如果落地时间小于PTZ延迟，直接预测落地位置
            predictedPosition = landingPoint;
        }

        PredictionResult result = new PredictionResult(predictedPosition, state);
        result.landingPoint = landingPoint;
        result.timeToLand = timeToLand;
        result.confidence = 0.8f; // 下落预测置信度较高

        return result;
    }

    /**
     * 预测目标在PTZ延迟后的位置（用于PTZ联动）
     * 
     * @param currentPosition 当前位置
     * @param velocity 当前速度
     * @param ptzDelaySeconds PTZ延迟时间（秒）
     * @return 预测结果
     */
    public PredictionResult predictForPTZ(Point currentPosition, float[] velocity, float ptzDelaySeconds) {
        MotionState state = detectMotionState(currentPosition, velocity);
        
        // 如果在下落，使用落地预测
        if (state == MotionState.FALLING) {
            PredictionResult landingResult = predictLanding(currentPosition, velocity);
            
            // 如果落地时间小于PTZ延迟，预测落地位置
            if (landingResult.timeToLand > 0 && landingResult.timeToLand <= ptzDelaySeconds) {
                landingResult.predictedPosition = landingResult.landingPoint;
                return landingResult;
            }
        }
        
        // 其他情况：预测PTZ延迟后的位置
        Point predictedPosition = predictPosition(currentPosition, velocity, ptzDelaySeconds);
        PredictionResult result = new PredictionResult(predictedPosition, state);
        
        // 根据轨迹长度和速度稳定性计算置信度
        float speed = (float) Math.sqrt(velocity[0] * velocity[0] + 
                                        velocity[1] * velocity[1] + 
                                        velocity[2] * velocity[2]);
        if (speed > 0.1f && speed < 10.0f) {
            result.confidence = 0.7f; // 速度合理，置信度较高
        } else {
            result.confidence = 0.4f; // 速度异常，置信度较低
        }
        
        return result;
    }
}
