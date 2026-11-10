from datetime import date
from typing import Annotated, Literal, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator


TaskId = Annotated[str, Field(pattern=r"^[A-Za-z0-9_-]{1,64}$")]
TaskKind = Literal["ANALYZE", "PLAN", "REPORT"]
ResultStatus = Literal["COMPLETED", "WAITING_INPUT", "WAITING_CONFIRMATION"]


class RequestModel(BaseModel):
    model_config = ConfigDict(extra="forbid")


class TaskSpec(BaseModel):
    goal: str = Field(min_length=1, max_length=2000)
    kind: TaskKind
    startDate: date
    endDate: date
    sourceName: str | None = Field(default=None, max_length=100)
    planStartDate: date | None = None
    planEndDate: date | None = None

    @model_validator(mode="after")
    def date_ranges(self) -> Self:
        if not 0 <= (self.endDate - self.startDate).days < 366:
            raise ValueError("统计日期范围必须为1至366天")
        if self.kind == "PLAN":
            if self.planStartDate is None or self.planEndDate is None:
                raise ValueError("PLAN必须显式提供planStartDate和planEndDate")
            if not 0 <= (self.planEndDate - self.planStartDate).days < 31:
                raise ValueError("计划日期范围必须为1至31天")
        return self


class RunRequest(TaskSpec, RequestModel):
    taskId: TaskId
    taskToken: str = Field(min_length=1, repr=False)


class ResumeRequest(RequestModel):
    taskToken: str = Field(min_length=1, repr=False)
    action: Literal["INPUT", "CONFIRM", "RETRY"]
    input: str | None = Field(default=None, min_length=1, max_length=4000)
    planId: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def action_payload(self) -> Self:
        if self.action == "INPUT" and self.input is None:
            raise ValueError("INPUT必须提供input")
        if self.action == "CONFIRM" and self.planId is None:
            raise ValueError("CONFIRM必须提供已保存的planId")
        return self


class TaskContext(TaskSpec):
    id: str | int
    status: str
    aiConsent: bool
    timezone: str
    planId: int | None = None


class DailySleep(BaseModel):
    date: date
    hours: float | None
    recordCount: int


class SleepStatistics(BaseModel):
    startDate: date
    endDate: date
    sourceName: str | None
    periodDays: int
    validDays: int
    coverageRate: float
    averageHours: float | None
    totalHours: float
    recordCount: int
    dataCutoff: str | None
    daily: list[DailySleep]


class DraftItem(RequestModel):
    date: date
    time: Annotated[str, Field(pattern=r"^(?:[01]\d|2[0-3]):[0-5]\d$")]
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(min_length=1, max_length=2000)


class PlanDraft(RequestModel):
    title: str = Field(min_length=1, max_length=200)
    reason: str = Field(min_length=1, max_length=4000)
    startDate: date
    endDate: date
    items: list[DraftItem] = Field(min_length=1, max_length=93)

    @model_validator(mode="after")
    def item_dates(self) -> Self:
        if not 0 <= (self.endDate - self.startDate).days < 31:
            raise ValueError("计划草稿日期范围必须为1至31天")
        if any(not self.startDate <= item.date <= self.endDate for item in self.items):
            raise ValueError("计划项目必须位于草稿日期范围内")
        return self


class ArticleReference(BaseModel):
    id: int
    title: str
    sourceName: str
    sourceUrl: str | None


class Article(ArticleReference):
    content: str
    updatedAt: str
    published: bool


class ModelReport(RequestModel):
    title: str = Field(min_length=1, max_length=200)
    content: str = Field(min_length=1, max_length=20000)
    referenceIds: list[int] = Field(max_length=30)


class ModelDecision(RequestModel):
    """Only model-authored text; statistics and source metadata come from real tools."""

    status: ResultStatus
    answer: str | None = Field(default=None, min_length=1, max_length=20000)
    question: str | None = Field(default=None, min_length=1, max_length=2000)
    draft: PlanDraft | None = None
    report: ModelReport | None = None

    @model_validator(mode="after")
    def result_shape(self) -> Self:
        if self.status == "WAITING_INPUT":
            if self.question is None or self.draft is not None or self.report is not None:
                raise ValueError("待补充时必须提供问题，不能生成计划或报告")
        elif self.status == "WAITING_CONFIRMATION":
            if self.draft is None or self.question is not None or self.report is not None:
                raise ValueError("待确认时必须提供计划草稿")
        elif self.answer is None or self.question is not None or self.draft is not None:
            raise ValueError("完成时必须提供回答且不能包含待处理问题或草稿")
        return self


class AgentReport(BaseModel):
    title: str
    content: str
    references: list[ArticleReference]


class AgentResult(BaseModel):
    status: ResultStatus
    answer: str | None = None
    question: str | None = None
    statistics: SleepStatistics | None = None
    draft: PlanDraft | None = None
    report: AgentReport | None = None


class EmptyArguments(RequestModel):
    pass


class KnowledgeArguments(RequestModel):
    query: str = Field(min_length=1, max_length=200)
