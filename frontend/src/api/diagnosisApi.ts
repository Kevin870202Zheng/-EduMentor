// ================================================================
// 学情诊断模块 API
// /api/diagnosis/analyze, /cognitive-profile, /radar, /heatmap, /overview
// ================================================================

import apiClient from './apiClient';
import type {
  DiagnosisResultDto,
  CognitiveProfileDto,
  RadarChartDataDto,
  HeatmapDataDto,
  DiagnosisOverviewDto,
} from './types';

export const diagnosisApi = {
  /**
   * 学情诊断分析
   * POST /api/diagnosis/analyze
   */
  async analyze(
    studentId?: string,
    courseId?: string,
    daysBack: number = 30,
  ): Promise<DiagnosisResultDto> {
    const params: Record<string, string | number> = { daysBack };
    if (studentId) params.studentId = studentId;
    if (courseId) params.courseId = courseId;
    return apiClient.post<unknown, DiagnosisResultDto>('/diagnosis/analyze', params);
  },

  /**
   * 认知画像
   * GET /api/diagnosis/cognitive-profile
   */
  async cognitiveProfile(studentId?: string): Promise<CognitiveProfileDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, CognitiveProfileDto>('/diagnosis/cognitive-profile', { params });
  },

  /**
   * 能力雷达图数据
   * GET /api/diagnosis/radar
   */
  async radarChart(studentId?: string): Promise<RadarChartDataDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, RadarChartDataDto>('/diagnosis/radar', { params });
  },

  /**
   * 热力图数据
   * GET /api/diagnosis/heatmap
   */
  async heatMap(
    studentId?: string,
    courseId?: string,
  ): Promise<HeatmapDataDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, HeatmapDataDto>('/diagnosis/heatmap', { params });
  },

  /**
   * 诊断总览
   * GET /api/diagnosis/overview
   */
  async overview(studentId?: string): Promise<DiagnosisOverviewDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, DiagnosisOverviewDto>('/diagnosis/overview', { params });
  },
};
