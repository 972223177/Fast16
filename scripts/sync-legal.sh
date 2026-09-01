#!/usr/bin/env bash
# 同步合规文本：app/src/main/assets/*.txt（唯一事实源）→ docs/*.html（商店公开 URL）
#
# 用法：./scripts/sync-legal.sh
# 修改隐私政策 / 用户协议文案时，只需改 assets 下的 .txt，然后运行本脚本重新生成 HTML。
# App 内弹窗（LegalDialog）读 assets 的 .txt，商店链接指向 docs 的 .html，二者内容由此保持一致。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$ROOT/app/src/main/assets"
DOCS="$ROOT/docs"

# 生成单个 HTML（t1=assets txt 前缀，t2=docs html 文件名——URL 惯例用连字符）
gen() {
  local txt_name="$1"
  local html_name="$2"
  local txt="$ASSETS/$txt_name.txt"
  local html="$DOCS/$html_name.html"
  [[ -f "$txt" ]] || { echo "缺少源文件: $txt" >&2; exit 1; }

  # 标题 = txt 首个非空行（如 "Fast16 816 轻断食 · 隐私政策"）
  local title
  title="$(awk 'NF { print; exit }' "$txt")"

  {
    cat <<'HEADER'
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
HEADER
    echo "<title>${title}</title>"
    cat <<'STYLE'
<style>
  body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif; line-height: 1.8; color: #333; max-width: 720px; margin: 0 auto; padding: 24px 16px 48px; }
  h1 { font-size: 20px; border-bottom: 2px solid #333; padding-bottom: 8px; }
  h2 { font-size: 16px; margin-top: 24px; }
  p { margin: 8px 0; }
  ol, ul { padding-left: 20px; }
  li { margin: 4px 0; }
  .meta { color: #888; font-size: 13px; }
  .copyright { margin-top: 32px; color: #888; font-size: 13px; }
</style>
</head>
<body>
STYLE
    # 文本 → HTML 结构：
    #   首非空行 → h1；"一、" 小节标题 → h2；"更新日期：" → .meta；Copyright → .copyright；
    #   "- " 列表 → ul/li；"1. " 列表 → ol/li；其余 → p
    awk '
      BEGIN { first = 1; list = "" }
      function close_list() {
        if (list == "ul") print "</ul>"
        else if (list == "ol") print "</ol>"
        list = ""
      }
      {
        if (length($0) == 0) { close_list(); next }
        if (first) { print "<h1>" $0 "</h1>"; first = 0; next }
        if ($0 ~ /^更新日期：/) { close_list(); print "<p class=\"meta\">" $0 "</p>"; next }
        if ($0 ~ /^Copyright/) { close_list(); print "<p class=\"copyright\">" $0 "</p>"; next }
        if ($0 ~ /^[一二三四五六七八九十]+、/) { close_list(); print "<h2>" $0 "</h2>"; next }
        if ($0 ~ /^[-*] /) {
          if (list != "ul") { close_list(); print "<ul>"; list = "ul" }
          print "<li>" substr($0, 3) "</li>"; next
        }
        if ($0 ~ /^[0-9]+\./) {
          if (list != "ol") { close_list(); print "<ol>"; list = "ol" }
          line = $0; sub(/^[0-9]+\. */, "", line)
          print "<li>" line "</li>"; next
        }
        close_list(); print "<p>" $0 "</p>"
      }
      END { close_list() }
    ' "$txt"
    echo '</body>'
    echo '</html>'
  } > "$html"

  echo "已生成: $html"
}

gen privacy_policy privacy-policy
gen user_agreement user-agreement
echo "完成：文案请只修改 $ASSETS 下的 .txt，重新运行本脚本同步。"
