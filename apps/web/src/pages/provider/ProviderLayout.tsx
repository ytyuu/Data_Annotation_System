import { RoleLayout } from '../shared/RoleLayout';

const menus = [
  { key: 'datasets', label: '数据集管理', path: '/provider/datasets' },
  { key: 'ai-annotations', label: '大模型标注', path: '/provider/ai-annotations' },
  { key: 'reviews', label: '标注审核', path: '/provider/reviews' },
  { key: 'disputes', label: '争议处理', path: '/provider/disputes' },
];

export function ProviderLayout() {
  return (
    <RoleLayout
      role="provider"
      menus={menus}
      workbenchPath="/provider/datasets"
      redirectOnRoleMismatch="/annotator/open-datasets"
    />
  );
}
