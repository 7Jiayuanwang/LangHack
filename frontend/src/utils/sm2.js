/**
 * SuperMemo-2 Algorithm (SM-2) Implementation
 *
 * @param {number} quality - Recall quality (0-5)
 * @param {Object} previousState - Previous state of the word
 * @param {number} previousState.repetition - Number of successful repetitions (n)
 * @param {number} previousState.interval - Previous interval in days (I)
 * @param {number} previousState.easinessFactor - Previous easiness factor (EF)
 * @param {Object} options - Optional configuration
 * @param {boolean} options.hardWordMode - Enable hard word mode (EF min 1.0, default false)
 * @returns {Object} New state { repetition, interval, easinessFactor, nextReviewTime }
 */
export function calculateSM2(quality, previousState = {}, options = {}) {
    // 输入验证: quality 必须在 0-5 范围内
    if (typeof quality !== 'number' || quality < 0 || quality > 5 || !Number.isFinite(quality)) {
        throw new Error('Quality must be a finite number between 0 and 5')
    }

    const { hardWordMode = false } = options;
    const minEF = hardWordMode ? 1.0 : 1.3;  // 困难词加强模式：更低的 EF 下限

    let {
        repetition = 0,
        interval = 0,
        easinessFactor = 2.5
    } = previousState;

    // 验证 previousState 数值有效性
    if (typeof repetition !== 'number' || repetition < 0 || !Number.isFinite(repetition)) {
        repetition = 0
    }
    if (typeof interval !== 'number' || interval < 0 || !Number.isFinite(interval)) {
        interval = 0
    }
    if (typeof easinessFactor !== 'number' || easinessFactor < minEF || easinessFactor > 5.0 || !Number.isFinite(easinessFactor)) {
        easinessFactor = 2.5
    }

    // CRITICAL FIX: Save the OLD EF value before updating it
    // SM-2 specification requires interval calculation to use the OLD EF value
    // The original implementation updated EF first, then used the NEW EF to calculate interval
    // This caused intervals to be systematically shorter than the standard algorithm
    const previousEF = easinessFactor;

    // 1. Update Repetition and Interval (using OLD EF value)
    if (quality >= 3) {
        // Success
        if (repetition === 0) {
            interval = 1;
        } else if (repetition === 1) {
            interval = 6;
        } else {
            // FIXED: Use previousEF (old value), not the newly calculated easinessFactor
            interval = Math.round(interval * previousEF);
        }
        repetition++;
    } else {
        // Failure
        repetition = 0;
        interval = 1;
    }

    // 2. Update Easiness Factor (EF) - AFTER interval calculation
    // EF = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
    easinessFactor = easinessFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));

    // EF boundaries: minEF ≤ EF ≤ 5.0
    if (easinessFactor < minEF) {
        easinessFactor = minEF;
    }
    if (easinessFactor > 5.0) {
        easinessFactor = 5.0;
    }

    // 3. Calculate Next Review Time
    // 计算今天午夜的时刻，确保时区边界稳定
    const today = new Date()
    const year = today.getFullYear()
    const month = today.getMonth()
    const date = today.getDate()
    // 直接用年月日构造新的 Date，确保为午夜
    const todayMidnight = new Date(year, month, date, 0, 0, 0, 0)

    const millisecondsPerDay = 24 * 60 * 60 * 1000
    const nextReviewTime = new Date(todayMidnight.getTime() + interval * millisecondsPerDay)

    return {
        repetition,
        interval,
        easinessFactor,
        nextReviewTime: nextReviewTime.toISOString()
    };
}
