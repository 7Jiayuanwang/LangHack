import { cet6Paper2Questions, cet6Paper3Questions } from './cet6December2024'
import { importedPastPapers } from './importedPastPapers'

export const examCategories = [
  { key: 'cet4', label: '英语四级', shortLabel: '四级', description: '大学英语四级历年真题' },
  { key: 'cet6', label: '英语六级', shortLabel: '六级', description: '大学英语六级历年真题' },
  { key: 'postgraduate', label: '考研英语', shortLabel: '考研', description: '考研英语一、英语二历年真题' }
]

const sampleQuestions = {
  cet4: [
    {
      id: 'cet4-reading-1',
      section: '阅读理解',
      passage: 'Many students believe that studying longer always leads to better results. Research, however, suggests that regular breaks help the brain retain information and keep attention from declining.',
      stem: 'What does the research suggest?',
      options: [
        'Long study sessions are always more effective.',
        'Regular breaks can improve learning efficiency.',
        'Students should avoid difficult material.',
        'Attention does not change over time.'
      ],
      answer: 1,
      analysis: '原文指出 regular breaks 能帮助大脑保留信息，并防止注意力下降，因此选 B。'
    },
    {
      id: 'cet4-vocabulary-1',
      section: '词汇理解',
      stem: 'The new library provides students with easy _____ to digital resources.',
      options: ['access', 'arrival', 'approach', 'appearance'],
      answer: 0,
      analysis: 'access to 是固定搭配，表示“使用或接触……的机会”。'
    },
    {
      id: 'cet4-listening-1',
      section: '听力理解',
      passage: 'Woman: Shall we take the bus to the museum? Man: The bus is delayed. Let us walk instead.',
      stem: 'How will the speakers probably go to the museum?',
      options: ['By bus.', 'By taxi.', 'On foot.', 'By bicycle.'],
      answer: 2,
      analysis: '男士建议 “Let us walk instead”，因此他们很可能步行前往。'
    }
  ],
  cet6: [
    {
      id: 'cet6-reading-1',
      section: '阅读理解',
      passage: 'Remote work has widened the talent pool for many companies, but it has also made informal communication more difficult. Successful teams therefore create deliberate opportunities for colleagues to exchange ideas.',
      stem: 'What do successful remote teams do according to the passage?',
      options: [
        'Hire fewer employees.',
        'Avoid informal discussions.',
        'Create intentional opportunities for communication.',
        'Require everyone to work longer hours.'
      ],
      answer: 2,
      analysis: '末句指出成功团队会主动创造同事交流想法的机会，对应 C。'
    },
    {
      id: 'cet6-vocabulary-1',
      section: '词汇理解',
      stem: 'The policy is intended to _____ the impact of rising energy costs on low-income families.',
      options: ['imitate', 'mitigate', 'motivate', 'meditate'],
      answer: 1,
      analysis: 'mitigate 表示“缓解、减轻”，与 impact 搭配符合语义。'
    },
    {
      id: 'cet6-translation-1',
      section: '翻译判断',
      stem: '“中国越来越重视公共图书馆在终身学习中的作用。”下列译文最准确的是：',
      options: [
        'China increasingly values the role of public libraries in lifelong learning.',
        'China increases public libraries to learn for a long life.',
        'Public libraries value China and its lifelong study.',
        'China was valuing public library in all lives.'
      ],
      answer: 0,
      analysis: 'A 准确表达“越来越重视”“公共图书馆的作用”和“终身学习”，语法也完整。'
    }
  ],
  postgraduate: [
    {
      id: 'post-reading-1',
      section: '阅读理解',
      passage: 'Technological tools do not determine how institutions change. Their effects depend on the incentives, routines and values already present in those institutions.',
      stem: 'The author argues that the effects of technology are mainly shaped by _____.',
      options: [
        'the price of new devices',
        'existing institutional conditions',
        'the age of their users',
        'government ownership'
      ],
      answer: 1,
      analysis: '第二句明确将技术效果归因于机构原有的激励、惯例和价值观，即 existing institutional conditions。'
    },
    {
      id: 'post-cloze-1',
      section: '完形填空',
      stem: 'A convincing argument should not merely state a conclusion; it should also _____ the evidence on which that conclusion rests.',
      options: ['conceal', 'examine', 'postpone', 'replace'],
      answer: 1,
      analysis: '论证不仅要给结论，还应 examine（审视）结论所依据的证据。'
    },
    {
      id: 'post-translation-1',
      section: '翻译判断',
      stem: 'Choose the best translation: “Critical thinking requires us to question assumptions that appear self-evident.”',
      options: [
        '批判性思维要求我们质疑那些看似不证自明的假设。',
        '批评的思想要求假设自己提供证据。',
        '我们需要用明显的假设代替批判。',
        '批判性思维说明所有假设都是错误的。'
      ],
      answer: 0,
      analysis: 'self-evident 指“不证自明的”，question assumptions 指“质疑假设”，A 最准确。'
    }
  ]
}

export const pastPapers = [
  ...importedPastPapers,
  { id: 'cet4-sample', category: 'cet4', title: '四级功能样题', date: '功能演示', duration: 15, status: 'ready', questions: sampleQuestions.cet4 },
  { id: 'cet6-sample', category: 'cet6', title: '六级功能样题', date: '功能演示', duration: 15, status: 'ready', questions: sampleQuestions.cet6 },
  { id: 'post-sample', category: 'postgraduate', title: '考研功能样题', date: '功能演示', duration: 15, status: 'ready', questions: sampleQuestions.postgraduate },
  { id: 'cet4-2024-12-1', category: 'cet4', title: '2024 年 12 月四级真题（第 1 套）', date: '2024-12', status: 'pending', questions: [] },
  { id: 'cet4-2024-12-2', category: 'cet4', title: '2024 年 12 月四级真题（第 2 套）', date: '2024-12', status: 'pending', questions: [] },
  { id: 'cet6-2024-12-1', category: 'cet6', title: '2024 年 12 月六级真题（第 1 套）', date: '2024-12', status: 'pending', questions: [] },
  { id: 'cet6-2024-12-2', category: 'cet6', title: '2024 年 12 月六级真题（第 2 套）', date: '2024-12', duration: 25, status: 'ready', scope: '仔细阅读', questions: cet6Paper2Questions },
  { id: 'cet6-2024-12-3', category: 'cet6', title: '2024 年 12 月六级真题（第 3 套）', date: '2024-12', duration: 25, status: 'ready', scope: '仔细阅读', questions: cet6Paper3Questions }
]
