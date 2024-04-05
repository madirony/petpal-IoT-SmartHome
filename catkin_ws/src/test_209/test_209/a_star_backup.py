import rclpy, time
import numpy as np
from rclpy.node import Node
import os
from geometry_msgs.msg import Pose,PoseStamped
from squaternion import Quaternion
from nav_msgs.msg import Odometry,OccupancyGrid,MapMetaData,Path
from math import pi,cos,sin
from collections import deque
from ros_log_package.RosLogPublisher import RosLogPublisher



class A_star(Node):

    def __init__(self):
        super().__init__('a_Star')
        # 로직 1. publisher, subscriber 만들기
        self.map_sub = self.create_subscription(OccupancyGrid,'map',self.map_callback,1)
        self.odom_sub = self.create_subscription(Odometry,'odom',self.odom_callback,1)
        self.goal_sub = self.create_subscription(PoseStamped,'goal_pose',self.goal_callback,1)
        self.a_star_pub= self.create_publisher(Path, 'global_path', 1)
        
        self.map_msg=OccupancyGrid()
        self.odom_msg=Odometry()
        self.is_map=False
        self.is_odom=False
        self.is_found_path=False
        self.is_grid_update=False

        self.is_param = False
        # 로직 2. 파라미터 설정
        self.goal = (0,0)
        self.map_size_x=700
        self.map_size_y=700
        self.map_resolution=0.05
        # self.map_offset_x=-8-8.75
        # self.map_offset_y=-4-8.75
    
        self.GRIDSIZE=700
 
        self.dx = [-1,0,0,1,-1,-1,1,1]
        self.dy = [0,1,-1,0,-1,1,-1,1]
        self.dCost = [1,1,1,1,1.414,1.414,1.414,1.414]
       
       # logging
        # self.ros_log_pub = None
        # try:
        #     self.ros_log_pub = RosLogPublisher(self)
        # except Exception as e:
        #     self.get_logger().error('Subscription initialization error: {}'.format(e))


    def grid_update(self):

        self.is_grid_update=True
        
        self.map_to_grid = np.array(self.map_msg.data).reshape((self.map_size_x, self.map_size_y)).transpose()

        for i in range(self.map_size_x):
            for j in range(self.map_size_y):
                # 값이 100인 원소 찾기
                if self.map_to_grid[i, j] == 100:
                    # 주변 원소 탐색
                    for dx in range(-5, 6):  # x 좌표 차이가 -2부터 2까지
                        for dy in range(-5, 6):  # y 좌표 차이가 -2부터 2까지
                            # 새로운 위치 계산
                            new_i = i + dx
                            new_j = j + dy
                            # 새로운 위치가 배열 범위 내에 있는지 확인
                            if 0 <= new_i < self.map_size_x and 0 <= new_j < self.map_size_y and self.map_to_grid[new_i, new_j] == 0:
                                # 조건에 맞는 주변 원소의 값을 100으로 설정
                                self.map_to_grid[new_i, new_j] = 101


    def pose_to_grid_cell(self,x,y):

        map_point_x = int((x - self.map_offset_x) / self.map_resolution)
        map_point_y = int((y - self.map_offset_y) / self.map_resolution)
        
        return map_point_x,map_point_y


    def grid_cell_to_pose(self,grid_cell):
        
        x = grid_cell[0] * self.map_resolution + self.map_offset_x
        y = grid_cell[1] * self.map_resolution + self.map_offset_y

        return [x,y]


    def odom_callback(self,msg):
        if self.is_param == False and self.is_map:
            self.is_param = True

            self.map_offset_x= self.map_msg.info.origin.position.x
            self.map_offset_y= self.map_msg.info.origin.position.y

        self.is_odom=True
        self.odom_msg=msg


    def map_callback(self,msg):
        self.is_map=True
        self.map_msg=msg
        self.is_grid_update = False
        

    def goal_callback(self,msg):
        start_time = time.time()
        if msg.header.frame_id=='map':
            goal_x=msg.pose.position.x
            goal_y=msg.pose.position.y
            goal_cell_x, goal_cell_y =self.pose_to_grid_cell(goal_x, goal_y)
            self.goal = (goal_cell_x, goal_cell_y)            

            if self.is_map ==True and self.is_odom==True and self.is_param==True:
                if self.is_grid_update==False :
                    self.grid_update()

                self.final_path=[]

                x=self.odom_msg.pose.pose.position.x
                y=self.odom_msg.pose.pose.position.y
                start_grid_cell=self.pose_to_grid_cell(x,y)

                self.path = [[0 for col in range(self.GRIDSIZE)] for row in range(self.GRIDSIZE)]
                self.cost = np.array([[self.GRIDSIZE*self.GRIDSIZE for col in range(self.GRIDSIZE)] for row in range(self.GRIDSIZE)], dtype=float)

                

                if start_grid_cell != self.goal :
                    self.a_star(start_grid_cell)
                else:
                    pass

                self.global_path_msg=Path()
                self.global_path_msg.header.frame_id='map'
                
                for grid_cell in reversed(self.final_path):
                    tmp_pose=PoseStamped()
                    waypoint_x,waypoint_y=self.grid_cell_to_pose(grid_cell)
                    tmp_pose.pose.position.x=waypoint_x
                    tmp_pose.pose.position.y=waypoint_y
                    tmp_pose.pose.orientation.w=1.0
                    self.global_path_msg.poses.append(tmp_pose)
            
                if len(self.final_path)!=0 :
                    self.a_star_pub.publish(self.global_path_msg)

                #end_time = time.time()  
                #elapsed_time = end_time - start_time
                #self.ros_log_pub.publish_log('astar', f'Subscription astar success time: {elapsed_time}')

                    
    def heuristic(self, node, goal):
        dx = abs(node[0] - goal[0])
        dy = abs(node[1] - goal[1])
        D = 1
        D2 = 1.414  # 대각선 이동 비용 (sqrt(2))
        return D * (dx + dy) + (D2 - 2 * D) * min(dx, dy)


    def a_star(self, start):
        Q = deque()
        Q.append((start, 0 + self.heuristic(start, self.goal)))  # 시작 노드와 시작 노드의 휴리스틱 비용을 큐에 추가
        self.cost[start[0]][start[1]] = 0
        found = False
        
        start_time = time.time()
        while Q:
            if time.time() - start_time >= 3:
                break
            current, _ = Q.popleft()
            
            if current == self.goal:
                found = True
                break

            for i in range(8):
                next = (current[0] + self.dx[i], current[1] + self.dy[i])
                if 0 <= next[0] < self.GRIDSIZE and 0 <= next[1] < self.GRIDSIZE:
                    if self.map_to_grid[next[0]][next[1]] < 50:
                        new_cost = self.cost[current[0]][current[1]] + self.dCost[i]
                        if self.cost[next[0]][next[1]] > new_cost:
                            heuristic_cost = new_cost + self.heuristic(next, self.goal)
                            Q.append((next, heuristic_cost))
                            Q = deque(sorted(Q, key=lambda x: x[1]))  # 우선순위 큐로 정렬
                            self.path[next[0]][next[1]] = current
                            self.cost[next[0]][next[1]] = new_cost

        if found:
            node = self.goal
            while node != start:
                self.final_path.append(node)
                node = self.path[node[0]][node[1]]
        #else:
            #self.ros_log_pub.publish_log('DEBUG', 'Subscription initialization error: astar goal not found')

                
def main(args=None):
    rclpy.init(args=args)

    global_planner = A_star()

    rclpy.spin(global_planner)


    global_planner.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    main()
