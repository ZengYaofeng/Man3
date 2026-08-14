import { useState } from 'react'
import { cn } from '@/lib/utils'

interface CoverImageProps {
  src?: string
  name: string
  className?: string
}

/** 封面图，加载失败时回退为名称首字色块 */
export default function CoverImage({ src, name, className }: CoverImageProps) {
  const [err, setErr] = useState(false)

  if (!src || err) {
    return (
      <div
        className={cn(
          'flex items-center justify-center bg-gradient-to-br from-indigo-200 to-slate-300 font-bold text-slate-600',
          className,
        )}
      >
        {name.slice(0, 1)}
      </div>
    )
  }

  return (
    <img
      src={src}
      alt={name}
      loading="lazy"
      onError={() => setErr(true)}
      className={cn('object-cover', className)}
    />
  )
}
