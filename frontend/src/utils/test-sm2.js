import { calculateSM2 } from './sm2.js';

function testSM2() {
    console.log('Running SM-2 Tests...\n');
    
    let testsPassed = 0;
    let testsFailed = 0;
    
    function assert(condition, message) {
        if (condition) {
            console.log(`✓ ${message}`);
            testsPassed++;
        } else {
            console.log(`✗ ${message}`);
            testsFailed++;
        }
    }

    // Test Case 1: First successful review (Quality 4)
    console.log('Test Case 1: First successful review (Quality 4)');
    const step1 = calculateSM2(4, {});
    assert(step1.repetition === 1, 'Step 1: Repetition should be 1');
    assert(step1.interval === 1, 'Step 1: Interval should be 1');
    assert(step1.easinessFactor === 2.5, 'Step 1: EF should be 2.5');

    // Test Case 2: Second successful review (Quality 5)
    console.log('\nTest Case 2: Second successful review (Quality 5)');
    const step2 = calculateSM2(5, step1);
    assert(step2.repetition === 2, 'Step 2: Repetition should be 2');
    assert(step2.interval === 6, 'Step 2: Interval should be 6');
    assert(step2.easinessFactor > 2.5, 'Step 2: EF should increase');

    // Test Case 3: Third successful review (Quality 3)
    console.log('\nTest Case 3: Third successful review (Quality 3)');
    const expectedInterval3 = Math.round(step2.interval * step2.easinessFactor);
    const step3 = calculateSM2(3, step2);
    assert(step3.repetition === 3, 'Step 3: Repetition should be 3');
    assert(step3.interval === expectedInterval3, `Step 3: Interval should be ${expectedInterval3}, got ${step3.interval}`);
    assert(step3.easinessFactor < step2.easinessFactor, 'Step 3: EF should decrease');

    // Test Case 4: Failure (Quality 1)
    console.log('\nTest Case 4: Failure (Quality 1)');
    const step4 = calculateSM2(1, step3);
    assert(step4.repetition === 0, 'Step 4: Repetition should reset to 0');
    assert(step4.interval === 1, 'Step 4: Interval should reset to 1');

    // Test Case 5: Edge cases - Quality boundaries
    console.log('\nTest Case 5: Quality boundary validation');
    try {
        calculateSM2(5.5);
        assert(false, 'Quality > 5 should throw error');
    } catch (e) {
        assert(true, 'Quality > 5 throws error: ' + e.message);
    }
    
    try {
        calculateSM2(-0.1);
        assert(false, 'Quality < 0 should throw error');
    } catch (e) {
        assert(true, 'Quality < 0 throws error: ' + e.message);
    }

    // Test Case 6: EF clamping
    console.log('\nTest Case 6: Easiness Factor clamping');
    const stepWorst = calculateSM2(0, { repetition: 5, interval: 100, easinessFactor: 2.5 });
    assert(stepWorst.easinessFactor >= 1.3, 'EF should be clamped to minimum 1.3');
    assert(stepWorst.easinessFactor <= 5.0, 'EF should be clamped to maximum 5.0');

    // Test Case 7: Invalid previousState auto-correction
    console.log('\nTest Case 7: Invalid previousState auto-correction');
    const stepInvalid = calculateSM2(4, { repetition: -5, interval: null, easinessFactor: 0.5 });
    assert(stepInvalid.repetition === 1, 'Invalid previousState should be corrected');
    assert(stepInvalid.interval === 1, 'Invalid interval should default to 1');

    // Test Case 8: nextReviewTime format validation
    console.log('\nTest Case 8: Next review time validation');
    const stepWithTime = calculateSM2(4, {});
    assert(typeof stepWithTime.nextReviewTime === 'string', 'nextReviewTime should be ISO string');
    assert(/^\d{4}-\d{2}-\d{2}T/.test(stepWithTime.nextReviewTime), 'nextReviewTime should be ISO 8601 format');

    // Summary
    console.log(`\n${'='.repeat(50)}`);
    console.log(`Tests passed: ${testsPassed}`);
    console.log(`Tests failed: ${testsFailed}`);
    console.log(`${'='.repeat(50)}`);
    
    if (testsFailed === 0) {
        console.log('\n✓ All tests passed!');
        process.exit(0);
    } else {
        console.log(`\n✗ ${testsFailed} test(s) failed!`);
        process.exit(1);
    }
}

testSM2();
