// 站点配置
export const SITE_CONFIG = {
  // 文章列表页面路径（动态获取当前域名，避免硬编码）
  ARTICLES_PATH: '/articles'
}

// 根据环境返回文章路径
export const getArticlesPath = () => {
  // 使用相对路径，自动适配当前域名
  // 在 langhack.imau.edu.cn 上 → https://langhack.imau.edu.cn/articles
  // 在 isa.imau.edu.cn 上 → https://isa.imau.edu.cn/articles
  return SITE_CONFIG.ARTICLES_PATH
}
