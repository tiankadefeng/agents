// frontend/src/composables/useTotTree.ts

import { shallowRef, triggerRef } from 'vue'
import type { TotNodeEvent, TotPruneEvent } from '@/types/agent'

/**
 * 前端树节点，从 TotNodeEvent 流重建。
 */
export interface TotTreeNode {
  nodeId: number
  level: number
  thought: string
  score: number
  parentId: number | null
  pruned: boolean
  children: TotTreeNode[]
}

/**
 * useTotTree - 从 SSE 事件流重建树结构 (Phase 8).
 *
 * 接收 TotNodeEvent 构建节点、TotPruneEvent 标记剪枝，
 * 提供叶子节点查询、最优路径回溯、层级过滤等方法。
 *
 * 使用 shallowRef 存储 Map<TotTreeNode>，内部 mutation 后调用
 * triggerRef 触发响应式重渲染。
 */
export function useTotTree() {
  const nodeMap = shallowRef<Map<number, TotTreeNode>>(new Map())
  const rootNodes = shallowRef<TotTreeNode[]>([])

  /**
   * 添加一个树节点到 nodeMap。
   * 若 parentId 非空则挂到父节点 children，否则加入 rootNodes。
   * 每次变更后调用 triggerRef 确保 shallowRef 内部 mutation 触发重渲染。
   */
  function addNode(ev: TotNodeEvent) {
    const node: TotTreeNode = {
      nodeId: ev.nodeId,
      level: ev.level,
      thought: ev.thought,
      score: ev.score,
      parentId: ev.parentId,
      pruned: false,
      children: [],
    }
    nodeMap.value.set(node.nodeId, node)

    if (node.parentId !== null) {
      const parent = nodeMap.value.get(node.parentId)
      if (parent) {
        parent.children = [...parent.children, node]
      }
    } else {
      rootNodes.value = [...rootNodes.value, node]
    }

    triggerRef(nodeMap)
  }

  /**
   * 标记剪枝：将 ev.prunedNodeIds 中对应节点的 pruned 置 true。
   */
  function markPruned(ev: TotPruneEvent) {
    for (const id of ev.prunedNodeIds) {
      const node = nodeMap.value.get(id)
      if (node) {
        node.pruned = true
      }
    }
    triggerRef(nodeMap)
  }

  /**
   * 获取所有叶子节点（children.length === 0）。
   */
  function getLeafNodes(): TotTreeNode[] {
    return Array.from(nodeMap.value.values()).filter(n => n.children.length === 0)
  }

  /**
   * 获取最优路径：从叶子节点中选最高分，沿 parentId 回溯到根。
   * 返回顺序为根 -> 叶子。
   */
  function getOptimalPath(): TotTreeNode[] {
    const leaves = getLeafNodes()
    if (leaves.length === 0) return []
    const best = leaves.reduce((a, b) => a.score >= b.score ? a : b)
    const path: TotTreeNode[] = []
    let current: TotTreeNode | undefined = best
    while (current) {
      path.unshift(current)
      current = current.parentId !== null
        ? nodeMap.value.get(current.parentId)
        : undefined
    }
    return path
  }

  /**
   * 获取指定层级的所有节点。
   */
  function getNodesByLevel(level: number): TotTreeNode[] {
    return Array.from(nodeMap.value.values()).filter(n => n.level === level)
  }

  /**
   * 重置树状态。
   */
  function clear() {
    nodeMap.value = new Map()
    rootNodes.value = []
  }

  return {
    nodeMap,
    rootNodes,
    addNode,
    markPruned,
    getLeafNodes,
    getOptimalPath,
    getNodesByLevel,
    clear,
  }
}

/**
 * 导出 useTotTree 的返回值类型，供 ReasoningPanel 类型导入。
 */
export type TotTree = ReturnType<typeof useTotTree>