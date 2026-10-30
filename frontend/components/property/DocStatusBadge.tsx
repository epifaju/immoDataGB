'use client';

import {useTranslations} from 'next-intl';
import {DOC_STATUSES, type DocStatus} from '@/lib/api/types';

const STATUS_CLASS: Record<DocStatus, string> = {
  none: 'bg-slate-100 text-slate-800',
  declared: 'bg-blue-100 text-blue-800',
  partial: 'bg-violet-100 text-violet-800',
  verified: 'bg-cyan-100 text-cyan-800',
  litigious: 'bg-pink-100 text-pink-800',
};

function isDocStatus(value: string): value is DocStatus {
  return (DOC_STATUSES as readonly string[]).includes(value);
}

export function DocStatusBadge({status}: {status: string}) {
  const t = useTranslations('property');
  const known = isDocStatus(status);
  const className = known ? STATUS_CLASS[status] : 'bg-slate-100 text-slate-800';
  const label = known ? t(`docStatus.${status}`) : status;
  return (
    <span className={`inline-flex items-center rounded px-2 py-0.5 text-sm ${className}`}>
      {label}
    </span>
  );
}
