import type { PermissionTreeNode } from "@/api/identity";

export type FlatPermission = {
  id: number;
  name: string;
  code?: string;
  type?: string;
  depth: number;
};

export function flattenPermissionTree(nodes: PermissionTreeNode[] | undefined, depth = 0): FlatPermission[] {
  const out: FlatPermission[] = [];
  for (const node of nodes ?? []) {
    if (node.id == null) {
      continue;
    }
    out.push({
      id: node.id,
      name: node.name ?? "",
      code: node.code,
      type: node.type,
      depth,
    });
    if (node.children && node.children.length > 0) {
      out.push(...flattenPermissionTree(node.children, depth + 1));
    }
  }
  return out;
}

export type PermissionTreeItem = {
  key: number;
  title: string;
  children?: PermissionTreeItem[];
};

export function toPermissionTreeData(nodes: PermissionTreeNode[] | undefined): PermissionTreeItem[] {
  return (nodes ?? [])
    .filter((node) => node.id != null)
    .map((node) => ({
      key: Number(node.id),
      title: node.name ?? "",
      children: node.children && node.children.length > 0 ? toPermissionTreeData(node.children) : undefined,
    }));
}
