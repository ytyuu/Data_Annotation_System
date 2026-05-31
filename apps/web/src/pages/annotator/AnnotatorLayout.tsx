import { RoleLayout } from '../shared/RoleLayout';

const menus = [
  { key: 'open-datasets', label: '可标注数据集', path: '/annotator/open-datasets' },
  { key: 'my-tasks', label: '我的任务', path: '/annotator/my-tasks' },
  { key: 'returned-tasks', label: '退回任务', path: '/annotator/returned-tasks' },
  { key: 'submissions', label: '提交记录', path: '/annotator/submissions' },
];

export function AnnotatorLayout() {
  return (
    <RoleLayout
      role="annotator"
      menus={menus}
      workbenchPath="/annotator/open-datasets"
      redirectOnRoleMismatch="/provider/datasets"
    />
  );
}
