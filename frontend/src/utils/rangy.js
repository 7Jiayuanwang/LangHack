// Rangy library wrapper
// Rangy 使用 UMD 包装器，Vite 环境下走 CommonJS 分支（module.exports），
// 不会设置 window.rangy，因此必须通过 import 获取模块导出。
// 插件（classapplier/serializer 等）通过 side-effect import 自动注册到 rangy 对象上。

import rangy from 'rangy/lib/rangy-core'
import 'rangy/lib/rangy-classapplier'
import 'rangy/lib/rangy-highlighter'
import 'rangy/lib/rangy-serializer'

export default rangy
