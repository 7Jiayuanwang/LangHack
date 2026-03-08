import { calculateSM2 } from './sm2.js';

function testSM2() {
    console.log('Running SM-2 Tests...');

    // Test Case 1: First successful review (Quality 4)
    const step1 = calculateSM2(4, {});
    console.assert(step1.repetition === 1, 'Step 1: Repetition should be 1');
    console.assert(step1.interval === 1, 'Step 1: Interval should be 1');
    console.assert(step1.easinessFactor === 2.5, 'Step 1: EF should be 2.5');

    // Test Case 2: Second successful review (Quality 5)
    const step2 = calculateSM2(5, step1);
    console.assert(step2.repetition === 2, 'Step 2: Repetition should be 2');
    console.assert(step2.interval === 6, 'Step 2: Interval should be 6');
    console.assert(step2.easinessFactor > 2.5, 'Step 2: EF should increase');

    // Test Case 3: Third successful review (Quality 3)
    const step3 = calculateSM2(3, step2);
    console.assert(step3.repetition === 3, 'Step 3: Repetition should be 3');
    console.assert(step3.interval === Math.round(step2.interval * step2.easinessFactor), `Step 3: Interval should be ${Math.round(step2.interval * step2.easinessFactor)}, got ${step3.interval}`);
    console.assert(step3.easinessFactor < step2.easinessFactor, 'Step 3: EF should decrease');

    // Test Case 4: Failure (Quality 1)
    const step4 = calculateSM2(1, step3);
    console.assert(step4.repetition === 0, 'Step 4: Repetition should reset to 0');
    console.assert(step4.interval === 1, 'Step 4: Interval should reset to 1');

    console.log('All tests passed!');
}

testSM2();
