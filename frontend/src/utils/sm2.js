/**
 * SuperMemo-2 Algorithm (SM-2) Implementation
 *
 * @param {number} quality - Recall quality (0-5)
 * @param {Object} previousState - Previous state of the word
 * @param {number} previousState.repetition - Number of successful repetitions (n)
 * @param {number} previousState.interval - Previous interval in days (I)
 * @param {number} previousState.easinessFactor - Previous easiness factor (EF)
 * @returns {Object} New state { repetition, interval, easinessFactor, nextReviewTime }
 */
export function calculateSM2(quality, previousState = {}) {
    let {
        repetition = 0,
        interval = 0,
        easinessFactor = 2.5
    } = previousState;

    // 1. Update Easiness Factor (EF)
    // EF = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
    easinessFactor = easinessFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));

    // EF boundaries: 1.3 ≤ EF ≤ 5.0
    if (easinessFactor < 1.3) {
        easinessFactor = 1.3;
    }
    if (easinessFactor > 5.0) {
        easinessFactor = 5.0;
    }

    // 2. Update Repetition and Interval
    if (quality >= 3) {
        // Success
        if (repetition === 0) {
            interval = 1;
        } else if (repetition === 1) {
            interval = 6;
        } else {
            interval = Math.round(interval * easinessFactor);
        }
        repetition++;
    } else {
        // Failure
        repetition = 0;
        interval = 1;
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
