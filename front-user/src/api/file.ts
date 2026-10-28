// ============================================================
// 用户端文件上传 API：对接后端 FileController（POST /file/upload?biz=xxx）
// biz 白名单：avatar/product/diy/flea/im/repair/recycle/kitchen/common
// 返回 {id, url, name}，url 形如 /api/file/preview/{biz}/... 可直接 <img> 展示
// ============================================================
import { request } from './request'

export interface UploadedFile {
  id: string
  url: string
  name: string
}

/** 上传单个文件（FormData，axios 自动 multipart；与 AvatarUploader 同链路） */
export function uploadFile(file: File | Blob, biz: string, fileName?: string) {
  const form = new FormData()
  form.append('file', file, fileName ?? (file instanceof File ? file.name : 'image.jpg'))
  return request.post<UploadedFile>(`/file/upload?biz=${biz}`, form)
}
