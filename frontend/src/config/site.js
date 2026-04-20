// 站点配置
export const SITE_CONFIG = {
  // 文章列表页面路径
  ARTICLES_PATH: 'https://langhack.imau.edu.cn/articles'
}

// 根据环境返回文章路径
export const getArticlesPath = () => {
  // 始终返回统一的路径
  return SITE_CONFIG.ARTICLES_PATH
}
